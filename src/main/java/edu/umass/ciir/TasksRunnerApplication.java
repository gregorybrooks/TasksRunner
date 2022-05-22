package edu.umass.ciir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@SpringBootApplication
public class TasksRunnerApplication {
    @Configuration
    public class SecurityConfig {

        @Bean
        SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
            return http
                    .requiresChannel(channel ->
                            channel.anyRequest().requiresSecure())
                    .authorizeRequests(authorize ->
                            authorize.anyRequest().permitAll())
                    .csrf().disable()
                    .build();
        }
    }

    public static void main(String[] args) {
        SpringApplication.run(TasksRunnerApplication.class, args);
    }

}
