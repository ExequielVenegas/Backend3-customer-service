package cl.duoc.bancoxyz.customer;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
@Configuration(proxyBeanMethods=false)
public class CustomerSecurity {
 @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
  return http.csrf(AbstractHttpConfigurer::disable)
   .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
   .authorizeHttpRequests(a->a.requestMatchers("/actuator/health").permitAll()
    .requestMatchers(HttpMethod.GET,"/internal/customers/*/saludo").hasAuthority("SCOPE_atm.customers.read")
    .requestMatchers(HttpMethod.GET,"/internal/customers/*/existencia").hasAnyAuthority("SCOPE_web.accounts.write","SCOPE_mobile.accounts.write","SCOPE_web.accounts.import")
    .requestMatchers(HttpMethod.GET,"/internal/customers","/internal/customers/**").hasAnyAuthority("SCOPE_web.customers.read","SCOPE_mobile.customers.read")
    .requestMatchers(HttpMethod.POST,"/internal/customers").hasAuthority("SCOPE_web.customers.write")
    .requestMatchers(HttpMethod.PUT,"/internal/customers/*").hasAuthority("SCOPE_web.customers.write")
    .requestMatchers("/actuator/info").hasAuthority("SCOPE_web.info")
    .anyRequest().denyAll())
   .oauth2ResourceServer(o->o.jwt(Customizer.withDefaults())).build();
 }
}

