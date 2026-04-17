package com.example.mustache_with_springboot_test.filter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.mustache.MustacheProperties;
import org.springframework.boot.web.servlet.view.MustacheViewResolver;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
public class MyFilter {

    @Autowired
    private final MustacheViewResolver viewResolver;

    @Autowired
    private final MustacheProperties properties;

    public MyFilter(MustacheViewResolver viewResolver, MustacheProperties properties) {
        System.out.println("properties.getServlet().getContentType() = " + properties.getServlet().getContentType());
        System.out.println("viewResolver = " + viewResolver);
        this.viewResolver = viewResolver;
        this.properties = properties;
    }

    @PostConstruct
    public void my() {
//        viewResolver.setContentType("text/html;charset=UTF-8");
    }

}
