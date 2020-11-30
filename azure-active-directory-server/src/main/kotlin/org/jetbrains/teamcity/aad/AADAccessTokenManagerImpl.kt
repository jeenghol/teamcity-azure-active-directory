/*
 * Copyright 2000-2020 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.teamcity.aad

import jetbrains.buildServer.serverSide.ServerSettings
import jetbrains.buildServer.serverSide.TeamCityProperties
import jetbrains.buildServer.serverSide.crypt.EncryptionManager
import jetbrains.buildServer.serverSide.auth.impl.TokenGenerator
import org.apache.log4j.Logger
import org.jose4j.jwa.AlgorithmConstraints
import org.jose4j.jwk.RsaJwkGenerator
import org.jose4j.jws.AlgorithmIdentifiers
import org.jose4j.jws.JsonWebSignature
import org.jose4j.jwt.JwtClaims
import org.jose4j.jwt.consumer.JwtConsumerBuilder
import org.jose4j.jwt.consumer.InvalidJwtException

class AADAccessTokenManagerImpl(
        private val serverSettings : ServerSettings,
        private val encryptionManager: EncryptionManager)
    : AADAccessTokenFactory, AADAccessTokenValidator {

    private val tokenGenerator = TokenGenerator()
    private val key = RsaJwkGenerator.generateJwk(RSA_KEY_LENGTH)
    private val algorithmConstraints = AlgorithmConstraints(
            AlgorithmConstraints.ConstraintType.WHITELIST, ALGORITHM_IDENTIFIER)

    override fun create(): String {
        val claims = createClaims()

        val jws = JsonWebSignature()
        jws.payload = claims.toJson()
        jws.key = key.privateKey
        jws.algorithmHeaderValue = ALGORITHM_IDENTIFIER
        return jws.compactSerialization
    }

    override fun validate(token: String): Boolean {
        val jwtConsumer = JwtConsumerBuilder()
                .setRequireExpirationTime()
                .setVerificationKey(key.getKey())
                .setJwsAlgorithmConstraints(algorithmConstraints)
                .build();

        try {
            val jwtClaims = jwtConsumer.processToClaims(token)
            if (jwtClaims.issuer != serverSettings.serverUUID) {
                LOG.warn("Incorrect issuer: ${jwtClaims.issuer}")
                return false
            }
        } catch (e: InvalidJwtException) {
            if (e.hasExpired()) {
                LOG.info("JWT token has expired", e)
            } else {
                LOG.warn("Exception occurred during JWT token processing", e)
            }
            return false
        }
        return true
    }

    private fun createClaims(): JwtClaims {
        var claims = JwtClaims()
        claims.issuer = serverSettings.serverUUID
        claims.setExpirationTimeMinutesInTheFuture(TeamCityProperties.getFloat(AADConstants.TTL_IN_MINUTES_PROPERTY, DEFAULT_TTL_IN_MINUTES))
        claims.setIssuedAtToNow()
        claims.setClaim(SALT_CLAIM, tokenGenerator.generateString(SALT_LENGTH))
        return claims
    }

    companion object {
        private val LOG = Logger.getLogger(AADAccessTokenManagerImpl::class.java.name)

        private const val ALGORITHM_IDENTIFIER = AlgorithmIdentifiers.RSA_USING_SHA256
        private const val RSA_KEY_LENGTH = 2048
        private const val SALT_CLAIM = "salt"
        private const val SALT_LENGTH = 64
        private const val DEFAULT_TTL_IN_MINUTES = 5f // 5 mins
    }
}