package com.cryptolab.service;
import com.cryptolab.exception.*;
import com.cryptolab.model.*;
import org.junit.jupiter.api.Test;
import java.util.*;import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;
class ScenarioServiceTest {
 @Test void switchesFromHeartbleedToPoodle(){ Fixture f=new Fixture();f.runtime=Map.of("heartbleed","MAINT","poodle","UP");assertEquals("poodle",f.service.activate("poodle","test").currentScenario());verify(f.proxy).enableBackend("poodle");verify(f.audit).record(eq("SCENARIO_SWITCH"),eq("heartbleed"),eq("poodle"),eq("SUCCESS"),anyString(),eq("test"),anyLong()); }
 @Test void missingTargetIsReported(){ Fixture f=new Fixture();when(f.registry.require("missing")).thenThrow(new ScenarioNotFoundException("missing"));assertThrows(ScenarioNotFoundException.class,()->f.service.activate("missing","test")); }
 @Test void unhealthyTargetIsRejected(){ Fixture f=new Fixture();when(f.docker.inspectContainer("poodle")).thenReturn(ContainerInspection.unavailable("stopped"));assertThrows(ScenarioUnhealthyException.class,()->f.service.activate("poodle","test")); }
 @Test void rollsBackWhenValidationFails(){ Fixture f=new Fixture();f.runtime=Map.of("heartbleed","MAINT","poodle","UP");doThrow(new VerificationException("bad")).when(f.validation).validateActivation(f.poodle);assertThrows(VerificationException.class,()->f.service.activate("poodle","x"));verify(f.proxy).enableBackend("heartbleed"); }
 @Test void proxyFailureTriggersRollback(){ Fixture f=new Fixture();doThrow(new ProxySwitchException("boom")).when(f.proxy).enableBackend("poodle");assertThrows(ProxySwitchException.class,()->f.service.activate("poodle","x"));verify(f.proxy).enableBackend("heartbleed"); }
 @Test void serializesConcurrentClicks() throws Exception { Fixture f=new Fixture();f.runtime=Map.of("heartbleed","MAINT","poodle","UP");ExecutorService pool=Executors.newFixedThreadPool(2);Future<?> a=pool.submit(()->f.service.activate("poodle","a"));Future<?> b=pool.submit(()->f.service.activate("poodle","b"));a.get();b.get();pool.shutdown();verify(f.proxy,atLeastOnce()).enableBackend("poodle"); }
 static class Fixture { ScenarioRegistry registry=mock(ScenarioRegistry.class);DockerService docker=mock(DockerService.class);ProxyService proxy=mock(ProxyService.class);ValidationService validation=mock(ValidationService.class);AuditService audit=mock(AuditService.class);ScenarioDefinition heart=definition("heartbleed"),poodle=definition("poodle"); Map<String,String> runtime=Map.of(); ScenarioService service;
  Fixture(){when(registry.require("poodle")).thenReturn(poodle);when(registry.findAll()).thenReturn(List.of(heart,poodle));when(docker.inspectContainer(anyString())).thenReturn(new ContainerInspection(true,true,true,"healthy",null,null,""));when(proxy.getActiveScenario()).thenReturn(Optional.of("heartbleed"));when(proxy.getRuntimeStatus()).thenAnswer(x->runtime);service=new ScenarioService(registry,docker,proxy,validation,audit);}
  static ScenarioDefinition definition(String id){ScenarioDefinition s=new ScenarioDefinition();s.setId(id);s.setName(id);s.setCve("CVE-x");s.setEnabled(true);ScenarioRuntime r=new ScenarioRuntime();r.setContainerName(id);r.setBackend(id);s.setRuntime(r);ScenarioDefinition.SwitchSpec sw=new ScenarioDefinition.SwitchSpec();sw.setMode("prewarmed");s.setSwitch(sw);return s;}
 }
}
