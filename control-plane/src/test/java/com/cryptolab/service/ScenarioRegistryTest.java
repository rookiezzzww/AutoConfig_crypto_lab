package com.cryptolab.service;
import com.cryptolab.config.LabProperties;
import com.cryptolab.exception.ProxySwitchException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class ScenarioRegistryTest {
 @TempDir Path dir;
 @Test void loadsYamlScenario() throws Exception { Files.createDirectories(dir.resolve("one")); Files.writeString(dir.resolve("one/scenario.yml"), yaml("one")); LabProperties p=new LabProperties();p.setScenariosPath(dir.toString()); ScenarioRegistry r=new ScenarioRegistry(p);r.load();assertEquals("one",r.require("one").getId()); }
 @Test void rejectsDuplicateIds() throws Exception { for(String n:java.util.List.of("a","b")){Files.createDirectories(dir.resolve(n));Files.writeString(dir.resolve(n+"/scenario.yml"),yaml("same"));} LabProperties p=new LabProperties();p.setScenariosPath(dir.toString()); ScenarioRegistry r=new ScenarioRegistry(p);assertThrows(ProxySwitchException.class,r::load); }
 @Test void resolvesVulnerabilityOptionFromEnvFile() throws Exception {
   Path scenario=dir.resolve("one"); Files.createDirectories(scenario);
   Files.writeString(scenario.resolve("scenario.yml"),yaml("one")+"vulnerabilityOptions:\n  - key: cipher\n    name: Cipher\n    envVar: LAB_CIPHER\n    defaultValue: weak\n    vulnerableValue: weak\n    allowedValues: [weak, strong]\n");
   Files.writeString(scenario.resolve("vulnerability.env"),"LAB_CIPHER=strong\n");
   LabProperties p=new LabProperties(); p.setScenariosPath(dir.toString()); ScenarioRegistry r=new ScenarioRegistry(p); r.load();
   assertEquals("strong",r.require("one").getVulnerabilityOptions().get(0).getEffectiveValue());
   assertFalse(r.require("one").getVulnerabilityOptions().get(0).isVulnerabilityConditionMet());
 }
 @Test void rejectsVulnerabilityOptionOutsideAllowList() throws Exception {
   Path scenario=dir.resolve("one"); Files.createDirectories(scenario);
   Files.writeString(scenario.resolve("scenario.yml"),yaml("one")+"vulnerabilityOptions:\n  - key: cipher\n    name: Cipher\n    envVar: LAB_CIPHER\n    defaultValue: weak\n    vulnerableValue: weak\n    allowedValues: [weak, strong]\n");
   Files.writeString(scenario.resolve("vulnerability.env"),"LAB_CIPHER=arbitrary-command\n");
   LabProperties p=new LabProperties(); p.setScenariosPath(dir.toString()); ScenarioRegistry r=new ScenarioRegistry(p);
   assertThrows(ProxySwitchException.class,r::load);
 }
 @Test void validatesSubmittedOptionAndAppliesEffectiveValue() throws Exception {
   Path scenario=dir.resolve("one"); Files.createDirectories(scenario);
   Files.writeString(scenario.resolve("scenario.yml"),yaml("one")+"vulnerabilityOptions:\n  - key: cipher\n    name: Cipher\n    envVar: LAB_CIPHER\n    defaultValue: weak\n    vulnerableValue: weak\n    allowedValues: [weak, strong]\n");
   Files.writeString(scenario.resolve("vulnerability.env"),"LAB_CIPHER=weak\n");
   LabProperties p=new LabProperties(); p.setScenariosPath(dir.toString()); ScenarioRegistry r=new ScenarioRegistry(p); r.load();
   var environment=r.resolveEnvironment("one",java.util.Map.of("cipher","strong"));
   assertEquals(java.util.Map.of("LAB_CIPHER","strong"),environment);
   r.applyEffectiveEnvironment("one",environment);
   assertEquals("strong",r.require("one").getVulnerabilityOptions().get(0).getEffectiveValue());
 }
 @Test void rejectsUnknownSubmittedOption() throws Exception {
   Path scenario=dir.resolve("one"); Files.createDirectories(scenario);
   Files.writeString(scenario.resolve("scenario.yml"),yaml("one"));
   LabProperties p=new LabProperties(); p.setScenariosPath(dir.toString()); ScenarioRegistry r=new ScenarioRegistry(p); r.load();
   assertThrows(com.cryptolab.exception.ConfigurationValidationException.class,
       ()->r.resolveEnvironment("one",java.util.Map.of("unexpected","value")));
 }
 private String yaml(String id){return "id: "+id+"\nname: demo\ncve: CVE-1\nruntime:\n  image: crypto-lab/demo:1.0\n  containerName: demo\n  containerPort: 8443\n  backend: demo\nswitch:\n  mode: ondemand\nenabled: true\n";}
}
