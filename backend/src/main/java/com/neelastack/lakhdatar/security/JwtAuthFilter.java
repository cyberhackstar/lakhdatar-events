package com.neelastack.lakhdatar.security;

import com.neelastack.lakhdatar.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;

@Component @RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {
 private final JwtService jwtService; private final UserRepository users;
 @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{
  String h=req.getHeader("Authorization");
  if(h!=null&&h.startsWith("Bearer ")){
   UserPrincipal token=jwtService.parse(h.substring(7));
   if(token!=null&&token.userId()!=null){
    var found=users.findById(token.userId()).filter(com.neelastack.lakhdatar.domain.User::isEnabled);
    if(found.isPresent()){
     var user=found.get();
     // Accounts created with an initial password must change it first; only the auth endpoints stay reachable.
     if(user.isMustChangePassword() && !req.getRequestURI().startsWith("/api/v1/auth/")){
      res.setStatus(403);res.setContentType("application/json");
      res.getWriter().write("{\"status\":403,\"code\":\"PASSWORD_CHANGE_REQUIRED\",\"message\":\"You must change your password before continuing\"}");
      return;
     }
     String role=user.getRole().name();UserPrincipal current=new UserPrincipal(user.getId(),user.getEmail(),role);var auth=new UsernamePasswordAuthenticationToken(current,null,List.of(new SimpleGrantedAuthority("ROLE_"+role)));SecurityContextHolder.getContext().setAuthentication(auth);
    }
   }
  }
  chain.doFilter(req,res);
 }
}
