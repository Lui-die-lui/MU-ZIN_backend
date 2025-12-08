package com.muzin.mu.zin.security.filter;

import com.muzin.mu.zin.repository.UserRepository;
import com.muzin.mu.zin.security.jwt.JwtUtils;
import jakarta.servlet.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;


@Component
public class JwtAuthnticationFilter implements Filter {

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private UserRepository userRepository;

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {

    }
}
