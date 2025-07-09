// SPDX-FileCopyrightText: 2025
//
// SPDX-License-Identifier: Apache-2.0
package io.github.a2a_4k.models

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AgentCardTest {

    @Test
    fun `test AgentCard creation with required fields`() {
        // Given
        val capabilities = Capabilities(
            streaming = true,
            pushNotifications = false,
            stateTransitionHistory = true,
        )

        val skill = Skill(
            id = "test-skill",
            name = "Test Skill",
        )

        // When
        val agentCard = AgentCard(
            name = "Test Agent",
            description = "Test Agent Description",
            url = "https://example.com",
            version = "1.0.0",
            capabilities = capabilities,
            defaultInputModes = listOf("text"),
            defaultOutputModes = listOf("text"),
            skills = listOf(skill),
        )

        // Then
        assertEquals("Test Agent", agentCard.name)
        assertEquals("Test Agent Description", agentCard.description)
        assertEquals("https://example.com", agentCard.url)
        assertEquals("1.0.0", agentCard.version)
        assertEquals(capabilities, agentCard.capabilities)
        assertEquals(listOf("text"), agentCard.defaultInputModes)
        assertEquals(listOf("text"), agentCard.defaultOutputModes)
        assertEquals(listOf(skill), agentCard.skills)
        assertNull(agentCard.provider)
        assertNull(agentCard.documentationUrl)
        assertNull(agentCard.authentication)
    }

    @Test
    fun `test AgentCard creation with all fields`() {
        // Given
        val capabilities = Capabilities(
            streaming = true,
            pushNotifications = true,
            stateTransitionHistory = true,
        )

        val authentication = Authentication(
            schemes = listOf("oauth2", "api_key"),
        )

        val provider = Provider(
            organization = "Test Organization",
            url = "https://test-org.com",
        )

        val skill = Skill(
            id = "test-skill",
            name = "Test Skill",
            description = "A test skill",
            tags = listOf("test", "example"),
            examples = listOf("Example 1", "Example 2"),
            inputModes = listOf("text", "file"),
            outputModes = listOf("text", "image"),
        )

        // When
        val agentCard = AgentCard(
            name = "Test Agent",
            description = "Test Agent Description",
            url = "https://example.com",
            version = "1.0.0",
            capabilities = capabilities,
            defaultInputModes = listOf("text", "file"),
            defaultOutputModes = listOf("text", "image"),
            skills = listOf(skill),
            provider = provider,
            documentationUrl = "https://docs.example.com",
            authentication = authentication,
        )

        // Then
        assertEquals("Test Agent", agentCard.name)
        assertEquals("Test Agent Description", agentCard.description)
        assertEquals("https://example.com", agentCard.url)
        assertEquals("1.0.0", agentCard.version)
        assertEquals(capabilities, agentCard.capabilities)
        assertEquals(listOf("text", "file"), agentCard.defaultInputModes)
        assertEquals(listOf("text", "image"), agentCard.defaultOutputModes)
        assertEquals(listOf(skill), agentCard.skills)
        assertNotNull(agentCard.provider)
        assertEquals("Test Organization", agentCard.provider?.organization)
        assertEquals("https://test-org.com", agentCard.provider?.url)
        assertEquals("https://docs.example.com", agentCard.documentationUrl)
        assertNotNull(agentCard.authentication)
        assertEquals(listOf("oauth2", "api_key"), agentCard.authentication?.schemes)
    }

    @Test
    fun `test AgentCard data class functionality`() {
        // Given
        val capabilities = Capabilities(streaming = true)
        val skill = Skill(id = "test-skill", name = "Test Skill")

        // When
        val agentCard1 = AgentCard(
            name = "Test Agent",
            description = "Test Agent Description",
            url = "https://example.com",
            version = "1.0.0",
            capabilities = capabilities,
            defaultInputModes = listOf("text"),
            defaultOutputModes = listOf("text"),
            skills = listOf(skill),
        )

        val agentCard2 = AgentCard(
            name = "Test Agent",
            description = "Test Agent Description",
            url = "https://example.com",
            version = "1.0.0",
            capabilities = capabilities,
            defaultInputModes = listOf("text"),
            defaultOutputModes = listOf("text"),
            skills = listOf(skill),
        )

        val agentCard3 = agentCard1.copy(name = "Different Agent")

        // Then
        // Test equals and hashCode
        assertEquals(agentCard1, agentCard2)
        assertEquals(agentCard1.hashCode(), agentCard2.hashCode())

        // Test copy
        assertEquals("Different Agent", agentCard3.name)
        assertEquals(agentCard1.description, agentCard3.description)
        assertEquals(agentCard1.url, agentCard3.url)
    }
}
