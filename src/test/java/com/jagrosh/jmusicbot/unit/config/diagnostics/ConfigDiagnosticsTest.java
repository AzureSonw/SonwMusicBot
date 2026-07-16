/*
 * Copyright 2026 Arif Banai (arif-banai)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jagrosh.jmusicbot.unit.config.diagnostics;

import com.jagrosh.jmusicbot.config.diagnostics.ConfigDiagnostics;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ConfigDiagnostics Unit Tests")
class ConfigDiagnosticsTest {
    
    @Nested
    @DisplayName("Missing Required Keys")
    class MissingRequiredKeysTests {
        
        @Test
        @DisplayName("detects missing token")
        void testDetectMissingKeys_token() {
            Map<String, Object> userMap = new HashMap<>();
            // Missing token in user config (already in new format)
            userMap.put("discord", Map.of("owner", 123456789L));
            Config migratedUserConfig = ConfigFactory.parseMap(userMap);
            
            Map<String, Object> defaultMap = new HashMap<>();
            Map<String, Object> discord = new HashMap<>();
            discord.put("token", "");
            discord.put("owner", 0L);
            defaultMap.put("discord", discord);
            Config defaults = ConfigFactory.parseMap(defaultMap);
            
            // Merged will have owner but NO token
            Config merged = migratedUserConfig.withFallback(defaults).resolve();
            ConfigDiagnostics.Report report = ConfigDiagnostics.analyze(migratedUserConfig, merged, defaults);
            
            assertTrue(report.getMissingRequired().contains("discord.token"));
        }
        
        @Test
        @DisplayName("detects missing owner")
        void testDetectMissingKeys_owner() {
            Map<String, Object> userMap = new HashMap<>();
            userMap.put("discord", Map.of("token", "test_token"));
            Config migratedUserConfig = ConfigFactory.parseMap(userMap);
            
            Map<String, Object> defaultMap = new HashMap<>();
            Map<String, Object> discord = new HashMap<>();
            discord.put("token", "");
            discord.put("owner", 0L);
            defaultMap.put("discord", discord);
            Config defaults = ConfigFactory.parseMap(defaultMap);
            
            Config merged = migratedUserConfig.withFallback(defaults).resolve();
            ConfigDiagnostics.Report report = ConfigDiagnostics.analyze(migratedUserConfig, merged, defaults);
            
            assertTrue(report.getMissingRequired().contains("discord.owner"));
        }
        
        @Test
        @DisplayName("no missing keys when all required present")
        void testDetectMissingKeys_allPresent() {
            Map<String, Object> userMap = new HashMap<>();
            userMap.put("discord", Map.of("token", "test_token", "owner", 123456789L));
            Config migratedUserConfig = ConfigFactory.parseMap(userMap);
            
            Map<String, Object> defaultMap = new HashMap<>();
            Map<String, Object> discord = new HashMap<>();
            discord.put("token", "");
            discord.put("owner", 0L);
            defaultMap.put("discord", discord);
            Config defaults = ConfigFactory.parseMap(defaultMap);
            
            Config merged = migratedUserConfig.withFallback(defaults).resolve();
            ConfigDiagnostics.Report report = ConfigDiagnostics.analyze(migratedUserConfig, merged, defaults);
            
            assertTrue(report.getMissingRequired().isEmpty());
        }
    }
    
    @Nested
    @DisplayName("Missing Optional Keys")
    class MissingOptionalKeysTests {
        
        @Test
        @DisplayName("detects missing optional keys from defaults")
        void testDetectMissingOptionalKeys() {
            Map<String, Object> userMap = new HashMap<>();
            userMap.put("discord", Map.of("token", "test_token", "owner", 123456789L));
            Config migratedUserConfig = ConfigFactory.parseMap(userMap);
            
            Map<String, Object> defaultMap = new HashMap<>();
            Map<String, Object> discord = new HashMap<>();
            discord.put("token", "");
            discord.put("owner", 0L);
            defaultMap.put("discord", discord);
            
            Map<String, Object> commands = new HashMap<>();
            commands.put("prefix", "@mention");
            defaultMap.put("commands", commands);
            
            Config defaults = ConfigFactory.parseMap(defaultMap);
            // Merged must NOT have the optional key for it to be reported as missing
            Config merged = migratedUserConfig; 
            
            ConfigDiagnostics.Report report = ConfigDiagnostics.analyze(migratedUserConfig, merged, defaults);
            
            // Should detect that commands.prefix is in defaults but not in merged config
            assertTrue(report.getMissingOptional().contains("commands.prefix"));
        }
    }
    
    @Nested
    @DisplayName("Deprecated Keys")
    class DeprecatedKeysTests {
        
        @Test
        @DisplayName("detects unknown top-level key")
        void testDetectDeprecatedKeys_unknownKey() {
            Map<String, Object> userMap = new HashMap<>();
            userMap.put("discord", Map.of("token", "test_token", "owner", 123456789L));
            userMap.put("unknownKey", "value");
            Config migratedUserConfig = ConfigFactory.parseMap(userMap);
            
            Map<String, Object> defaultMap = new HashMap<>();
            Map<String, Object> discord = new HashMap<>();
            discord.put("token", "");
            discord.put("owner", 0L);
            defaultMap.put("discord", discord);
            Config defaults = ConfigFactory.parseMap(defaultMap);
            
            Config merged = migratedUserConfig.withFallback(defaults).resolve();
            ConfigDiagnostics.Report report = ConfigDiagnostics.analyze(migratedUserConfig, merged, defaults);
            
            assertTrue(report.getDeprecated().contains("unknownKey"));
        }
        
        @Test
        @DisplayName("ignores meta.configVersion")
        void testDetectDeprecatedKeys_ignoresMetaVersion() {
            Map<String, Object> userMap = new HashMap<>();
            Map<String, Object> meta = new HashMap<>();
            meta.put("configVersion", 1);
            userMap.put("meta", meta);
            userMap.put("discord", Map.of("token", "test_token", "owner", 123456789L));
            Config migratedUserConfig = ConfigFactory.parseMap(userMap);
            
            Map<String, Object> defaultMap = new HashMap<>();
            Map<String, Object> discord = new HashMap<>();
            discord.put("token", "");
            discord.put("owner", 0L);
            defaultMap.put("discord", discord);
            Config defaults = ConfigFactory.parseMap(defaultMap);
            
            Config merged = migratedUserConfig.withFallback(defaults).resolve();
            ConfigDiagnostics.Report report = ConfigDiagnostics.analyze(migratedUserConfig, merged, defaults);
            
            // meta.configVersion should not be flagged as deprecated
            assertFalse(report.getDeprecated().contains("meta.configVersion"));
        }
        
        @Test
        @DisplayName("detects nested unknown key")
        void testDetectDeprecatedKeys_nestedUnknown() {
            Map<String, Object> userMap = new HashMap<>();
            Map<String, Object> commands = new HashMap<>();
            commands.put("unknownCommand", "value");
            userMap.put("commands", commands);
            userMap.put("discord", Map.of("token", "test_token", "owner", 123456789L));
            Config migratedUserConfig = ConfigFactory.parseMap(userMap);
            
            Map<String, Object> defaultMap = new HashMap<>();
            Map<String, Object> discord = new HashMap<>();
            discord.put("token", "");
            discord.put("owner", 0L);
            defaultMap.put("discord", discord);
            
            Map<String, Object> defaultCommands = new HashMap<>();
            defaultCommands.put("prefix", "@mention");
            defaultMap.put("commands", defaultCommands);
            
            Config defaults = ConfigFactory.parseMap(defaultMap);
            Config merged = migratedUserConfig.withFallback(defaults).resolve();
            
            ConfigDiagnostics.Report report = ConfigDiagnostics.analyze(migratedUserConfig, merged, defaults);
            
            assertTrue(report.getDeprecated().contains("commands.unknownCommand"));
        }
    }
    
    @Nested
    @DisplayName("Report Methods")
    class ReportMethodsTests {
        
        @Test
        @DisplayName("hasIssues returns true when issues present")
        void testHasIssues() {
            Map<String, Object> userMap = new HashMap<>();
            userMap.put("unknownKey", "value");
            Config migratedUserConfig = ConfigFactory.parseMap(userMap);
            
            Config defaults = ConfigFactory.empty();
            Config merged = migratedUserConfig.withFallback(defaults).resolve();
            
            ConfigDiagnostics.Report report = ConfigDiagnostics.analyze(migratedUserConfig, merged, defaults);
            
            assertTrue(report.hasIssues());
        }
        
        @Test
        @DisplayName("hasErrors returns true when required keys missing")
        void testHasErrors() {
            Map<String, Object> userMap = new HashMap<>();
            Config migratedUserConfig = ConfigFactory.parseMap(userMap);
            
            Map<String, Object> defaultMap = new HashMap<>();
            Map<String, Object> discord = new HashMap<>();
            discord.put("token", "");
            discord.put("owner", 0L);
            defaultMap.put("discord", discord);
            Config defaults = ConfigFactory.parseMap(defaultMap);
            
            Config merged = migratedUserConfig.withFallback(defaults).resolve();
            ConfigDiagnostics.Report report = ConfigDiagnostics.analyze(migratedUserConfig, merged, defaults);
            
            assertTrue(report.hasErrors());
        }
        
        @Test
        @DisplayName("hasWarnings returns true when optional keys missing or deprecated keys present")
        void testHasWarnings() {
            Map<String, Object> userMap = new HashMap<>();
            userMap.put("discord", Map.of("token", "test_token", "owner", 123456789L));
            userMap.put("unknownKey", "value");
            Config migratedUserConfig = ConfigFactory.parseMap(userMap);
            
            Map<String, Object> defaultMap = new HashMap<>();
            Map<String, Object> discord = new HashMap<>();
            discord.put("token", "");
            discord.put("owner", 0L);
            defaultMap.put("discord", discord);
            Config defaults = ConfigFactory.parseMap(defaultMap);
            
            Config merged = migratedUserConfig.withFallback(defaults).resolve();
            ConfigDiagnostics.Report report = ConfigDiagnostics.analyze(migratedUserConfig, merged, defaults);
            
            assertTrue(report.hasWarnings());
        }
        
        @Test
        @DisplayName("generateMessage formats diagnostic message correctly")
        void testGenerateMessage() {
            Map<String, Object> userMap = new HashMap<>();
            userMap.put("unknownKey", "value");
            Config migratedUserConfig = ConfigFactory.parseMap(userMap);
            
            Config defaults = ConfigFactory.empty();
            Config merged = migratedUserConfig.withFallback(defaults).resolve();
            
            ConfigDiagnostics.Report report = ConfigDiagnostics.analyze(migratedUserConfig, merged, defaults);
            String message = report.generateMessage();
            
            assertNotNull(message);
            assertTrue(message.contains("WARN") || message.contains("ERROR"));
        }
    }
}
