package org.springframework.context.annotation.configuration.spr9031;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.Arrays;

/**
 * Created by xujiankang on 2017/9/18.
 */
@Configuration
public class TestProfile {

    public static void main(String[] args) {
        System.setProperty("spring.profiles.active", "dev");
        ApplicationContext context = new AnnotationConfigApplicationContext(TestProfile.class);
        System.out.println(Arrays.asList(context.getBeanNamesForType(String.class)));
    }

    @Bean
    @Profile("test")
    public String str1(){
        return "str1";
    }


    @Bean
    @Profile("dev")
    public String str2(){
        return "str2";
    }


    @Bean
    public String str3(){
        return "str3";
    }

}
