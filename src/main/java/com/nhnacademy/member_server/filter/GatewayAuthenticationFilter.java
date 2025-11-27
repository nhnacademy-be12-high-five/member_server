package com.nhnacademy.member_server.filter;

import com.nhnacademy.member_server.entity.MemberPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class GatewayAuthenticationFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        String memberIdStr = request.getHeader("X-User-ID");
        String loginId = request.getHeader("X-Login-ID");
        String role = request.getHeader("X-Role");

        if (memberIdStr != null && !memberIdStr.isEmpty()) {
            String roleName = (role != null) ? role : "USER";
            if (!roleName.startsWith("ROLE_")) {
                roleName = "ROLE_" + roleName;
            }
            SimpleGrantedAuthority authority = new SimpleGrantedAuthority(roleName);

            Long memberId = Long.parseLong(memberIdStr);
            MemberPrincipal principal = new MemberPrincipal(memberId, loginId, roleName);

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(principal, null, Collections.singleton(authority));

            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }
}