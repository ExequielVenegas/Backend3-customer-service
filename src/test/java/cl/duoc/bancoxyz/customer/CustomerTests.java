package cl.duoc.bancoxyz.customer;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest @AutoConfigureMockMvc
class CustomerTests {
 @Autowired CustomerService service;
 @Autowired JdbcTemplate db;
 @Autowired MockMvc mvc;
 @Autowired ObjectMapper json;
 @BeforeEach void reset() { db.update("DELETE FROM customer_audit"); db.update("DELETE FROM customers"); }
 @Test void atmCanReadGreetingButNotPersonalProfile() throws Exception {
  var c=service.create(UUID.randomUUID(),"web-demo",profile("DOC-ATM"));
  var auth=jwt().authorities(new SimpleGrantedAuthority("SCOPE_atm.customers.read"));
  mvc.perform(get("/internal/customers/"+c.clienteId()+"/saludo").with(auth)).andExpect(status().isOk())
   .andExpect(jsonPath("$.nombre").exists()).andExpect(jsonPath("$.documento").doesNotExist()).andExpect(jsonPath("$.email").doesNotExist());
  mvc.perform(get("/internal/customers/"+c.clienteId()).with(auth)).andExpect(status().isForbidden());
 }
 @Test void accountWriterCanValidateExistenceWithoutReadingProfile() throws Exception {
  var customer=service.create(UUID.randomUUID(),"web-demo",profile(null));
  var auth=jwt().authorities(new SimpleGrantedAuthority("SCOPE_web.accounts.write"));
  mvc.perform(get("/internal/customers/"+customer.clienteId()+"/existencia").with(auth))
   .andExpect(status().isOk()).andExpect(jsonPath("$.clienteId").value(customer.clienteId().toString()))
   .andExpect(jsonPath("$.nombre").doesNotExist());
  mvc.perform(get("/internal/customers/"+customer.clienteId()).with(auth)).andExpect(status().isForbidden());
 }
 CustomerInput profile(String doc) { return new CustomerInput("Cliente Demo",doc,"demo@example.invalid",null,null); }
 @Test void flywayMigratesAndDuplicateDeliveryCreatesOneCustomerAndAudit() {
  UUID key=UUID.randomUUID();
  var first=service.create(key,"web-demo",profile("DEMO-1"));
  assertEquals(first,service.create(key,"web-demo",profile("DEMO-1")));
  assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM customers",Integer.class));
  assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM customer_audit",Integer.class));
 }
 @Test void repeatedNameDoesNotMergePeopleAndMissingDocumentIsAllowed() {
  var a=service.create(UUID.randomUUID(),"web-demo",profile(null));
  var b=service.create(UUID.randomUUID(),"web-demo",profile(null));
  assertNotEquals(a.clienteId(),b.clienteId());
  assertNull(a.documento());
 }
 @Test void existingDocumentConflictsWithoutExtraAudit() {
  service.create(UUID.randomUUID(),"web-demo",profile("DEMO-1"));
  assertThrows(CustomerFailure.class,()->service.create(UUID.randomUUID(),"web-demo",profile("demo-1")));
  assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM customer_audit",Integer.class));
 }
 @Test void changedPayloadWithSameKeyIsRejected() {
  UUID key=UUID.randomUUID();service.create(key,"web-demo",profile(null));
  assertThrows(CustomerFailure.class,()->service.create(key,"web-demo",profile("NEW")));
 }
 @Test void updateRequiresCurrentVersionAndAuditsOnlySuccessfulWrite() {
  var first=service.create(UUID.randomUUID(),"web-demo",profile(null));
  var updated=service.update(first.clienteId(),0,"web-demo",profile("NEW"));
  assertEquals(1,updated.version());
  assertThrows(CustomerFailure.class,()->service.update(first.clienteId(),0,"web-demo",profile("OLD")));
  assertEquals("NEW",service.find(first.clienteId()).documento());
  assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM customer_audit",Integer.class));
 }
 @Test void concurrentCreationIsIdempotent() throws Exception {
  UUID key=UUID.randomUUID();
  try(var pool=Executors.newFixedThreadPool(4)) {
   List<Callable<CustomerView>> calls=List.of(()->service.create(key,"web-demo",profile(null)),()->service.create(key,"web-demo",profile(null)),()->service.create(key,"web-demo",profile(null)));
   var ids=new HashSet<UUID>();for(var f:pool.invokeAll(calls)) ids.add(f.get().clienteId());
   assertEquals(1,ids.size());
  }
  assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM customer_audit",Integer.class));
 }
 @Test void directAnonymousAndWrongScopeAreRejected() throws Exception {
  mvc.perform(get("/internal/customers")).andExpect(status().isUnauthorized());
  mvc.perform(get("/internal/customers").with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_web.read")))).andExpect(status().isForbidden());
 }
 @Test void invalidInputAndMissingKeyAre400() throws Exception {
  mvc.perform(post("/internal/customers").with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_web.customers.write")))
   .contentType("application/json").content("{\"nombre\":\"Demo\"}")).andExpect(status().isBadRequest());
  mvc.perform(post("/internal/customers").with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_web.customers.write")))
   .header("Idempotency-Key",UUID.randomUUID()).contentType("application/json").content("{\"nombre\":\"  \",\"email\":\"invalid\"}")).andExpect(status().isBadRequest());
  assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM customers",Integer.class));
 }
 @Test void restCreateFindUpdateAndConflict() throws Exception {
  String created=mvc.perform(post("/internal/customers").with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_web.customers.write")))
   .header("Idempotency-Key",UUID.randomUUID()).contentType("application/json").content(json.writeValueAsString(profile(null))))
   .andExpect(status().isCreated()).andExpect(jsonPath("$.version").value(0)).andReturn().getResponse().getContentAsString();
  String id=json.readTree(created).get("clienteId").asText();
  mvc.perform(get("/internal/customers/"+id).with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_web.customers.read")))).andExpect(status().isOk());
  String change=json.writeValueAsString(new CustomerController.Update(0L,profile("NEW")));
  mvc.perform(put("/internal/customers/"+id).with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_web.customers.write")))
   .contentType("application/json").content(change)).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
  mvc.perform(put("/internal/customers/"+id).with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_web.customers.write")))
   .contentType("application/json").content(change)).andExpect(status().isConflict());
 }
 @Test void paginationIsBoundedAndMissingCustomerIs404() throws Exception {
  mvc.perform(get("/internal/customers?size=1000").with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_web.customers.read")))).andExpect(status().isBadRequest());
  mvc.perform(get("/internal/customers/"+UUID.randomUUID()).with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_web.customers.read")))).andExpect(status().isNotFound());
 }
}
