package org.nop.sandbox;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootApplication
public class SandboxApplication {

    @Autowired
    JdbcTemplate jdbcTemplate;

    public static void main(String[] args) {
        SpringApplication.run(SandboxApplication.class, args);
    }

    @PostConstruct
    public void scratch() {
        String queryResult = String.join("\n", jdbcTemplate.query("select * from students",
                (rs, rowNum) -> String.format("%s %s",
                        rs.getLong("id"), rs.getString("name"))));
        System.out.println("Query results:\n" + queryResult);
    }
}
