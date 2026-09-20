package com.cryptolab.service;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ProxyServiceTest {
 @Test void parsesRuntimeStatusAndSendsSafeCommands(){ List<String> commands=new ArrayList<>(); HaproxyRuntimeClient c=cmd->{commands.add(cmd);return cmd.equals("show stat")?"# pxname,svname,status\ncrypto_scenarios,heartbleed,MAINT\ncrypto_scenarios,poodle,UP\n":"";}; ProxyService s=new ProxyService(c); assertEquals("poodle",s.getActiveScenario().orElseThrow());s.enableBackend("poodle");s.disableBackend("heartbleed");assertTrue(commands.contains("set server crypto_scenarios/poodle state ready")); }
 @Test void rejectsUnsafeBackend(){ ProxyService s=new ProxyService(c->"");assertThrows(RuntimeException.class,()->s.enableBackend("x; shutdown")); }
}
