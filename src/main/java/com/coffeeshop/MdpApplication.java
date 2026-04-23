package com.coffeeshop;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.security.web.SecurityFilterChain;

@SpringBootApplication(exclude = {
        UserDetailsServiceAutoConfiguration.class,
        ManagementWebSecurityAutoConfiguration.class
})
public class MdpApplication {

    public static void main(String[] args) {
        SpringApplication.run(MdpApplication.class, args);
    }

    @Bean
    public ApplicationRunner diagnosticRunner(ApplicationContext ctx) {
        return args -> {
            System.out.println("=== ManagementWebSecurityAutoConfiguration bean present: " +
                    ctx.containsBean("managementSecurityFilterChain"));
            System.out.println("=== Total SecurityFilterChain beans: " +
                    ctx.getBeanNamesForType(SecurityFilterChain.class).length);
            // Should print your bean name only, e.g. "filterChain"
            for (String name : ctx.getBeanNamesForType(SecurityFilterChain.class)) {
                System.out.println("    Chain: " + name);
            }
        };
    }
}
