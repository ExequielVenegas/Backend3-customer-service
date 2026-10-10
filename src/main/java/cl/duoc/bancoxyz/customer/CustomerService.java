package cl.duoc.bancoxyz.customer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
@Service
public class CustomerService {
 private final JdbcTemplate db;
 private final ObjectMapper json;
 public CustomerService(JdbcTemplate db,ObjectMapper json) { this.db=db; this.json=json; }
 private static final RowMapper<CustomerView> MAPPER=(r,n)->new CustomerView(UUID.fromString(r.getString("customer_id")),
  r.getString("nombre"),r.getString("documento"),r.getString("email"),r.getString("telefono"),
  r.getString("direccion"),r.getString("origen"),r.getLong("version"));
 public CustomerView find(UUID id) {
  return db.query("SELECT * FROM customers WHERE customer_id=?",MAPPER,id.toString()).stream().findFirst()
   .orElseThrow(()->new CustomerFailure(HttpStatus.NOT_FOUND,"Cliente no encontrado"));
 }
 public List<CustomerView> list(int page,int size) {
  return db.query("SELECT * FROM customers ORDER BY created_at,customer_id LIMIT ? OFFSET ?",MAPPER,size,(long)page*size);
 }
 @Transactional
 public CustomerView create(UUID key,String actor,CustomerInput input) {
  actor=actor(actor);
  var data=normalize(input);
  UUID id=UUID.nameUUIDFromBytes((actor+":"+key).getBytes(StandardCharsets.UTF_8));
  String hash=hash(data);
  try {
   db.update("INSERT INTO customers(customer_id,creation_hash,created_by,nombre,documento,email,telefono,direccion,origen) VALUES(?,?,?,?,?,?,?,?,?)",
    id.toString(),hash,actor,data.nombre(),data.documento(),data.email(),data.telefono(),data.direccion(),"MODERNO");
  } catch(DuplicateKeyException ex) {
   var rows=db.queryForList("SELECT creation_hash FROM customers WHERE customer_id=? FOR UPDATE",id.toString());
   if(rows.isEmpty()) throw new CustomerFailure(HttpStatus.CONFLICT,"Documento ya registrado");
   if(!hash.equals(rows.getFirst().get("creation_hash")))
    throw new CustomerFailure(HttpStatus.CONFLICT,"Idempotency-Key reutilizada con otro contenido");
   return find(id);
  }
  audit(id,actor,"ALTA",0);
  return find(id);
 }
 @Transactional
 public CustomerView update(UUID id,long version,String actor,CustomerInput input) {
  actor=actor(actor);
  var data=normalize(input);
  int updated;
  try {
   updated=db.update("UPDATE customers SET nombre=?,documento=?,email=?,telefono=?,direccion=?,version=version+1,updated_at=CURRENT_TIMESTAMP(6) WHERE customer_id=? AND version=?",
    data.nombre(),data.documento(),data.email(),data.telefono(),data.direccion(),id.toString(),version);
  } catch(DuplicateKeyException ex) { throw new CustomerFailure(HttpStatus.CONFLICT,"Documento ya registrado"); }
  if(updated==0) {
   find(id);
   throw new CustomerFailure(HttpStatus.CONFLICT,"Version desactualizada; consulta el cliente antes de editar");
  }
  audit(id,actor,"ACTUALIZACION",version+1);
  return find(id);
 }
 private void audit(UUID id,String actor,String action,long version) {
  db.update("INSERT INTO customer_audit(audit_id,customer_id,actor,accion,version) VALUES(?,?,?,?,?)",
   UUID.randomUUID().toString(),id.toString(),actor,action,version);
 }
 private String actor(String value) {
  if(value==null || value.isBlank() || value.length()>100) throw new CustomerFailure(HttpStatus.FORBIDDEN,"Identidad no admitida");
  return value;
 }
 private CustomerInput normalize(CustomerInput i) {
  String doc=clean(i.documento());
  return new CustomerInput(i.nombre().trim(),doc==null?null:doc.toUpperCase(Locale.ROOT),clean(i.email()),clean(i.telefono()),clean(i.direccion()));
 }
 private String clean(String value) { return value==null || value.isBlank()?null:value.trim(); }
 private String hash(CustomerInput data) {
  try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(data))); }
  catch(java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
 }
}
