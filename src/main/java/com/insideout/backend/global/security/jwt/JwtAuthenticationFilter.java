package com.insideout.backend.global.security.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final String BEARER_PREFIX = "Bearer ";
	private static final String ACCESS_TOKEN_TYPE = "access";

	private final JwtTokenProvider jwtTokenProvider;

	@Override
	protected void doFilterInternal(
		@NonNull HttpServletRequest request,
		@NonNull HttpServletResponse response,
		@NonNull FilterChain filterChain
	) throws ServletException, IOException {

		String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);

		if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
			String token = authorization.substring(BEARER_PREFIX.length());

			if (jwtTokenProvider.validateToken(token)
				&& ACCESS_TOKEN_TYPE.equals(jwtTokenProvider.getTokenType(token))
				&& SecurityContextHolder.getContext().getAuthentication() == null) {

				String subject = jwtTokenProvider.getSubject(token);

				UsernamePasswordAuthenticationToken authentication =
					new UsernamePasswordAuthenticationToken(
						subject,
						null,
						Collections.emptyList()
					);
				authentication.setDetails(
					new WebAuthenticationDetailsSource().buildDetails(request)
				);

				SecurityContextHolder.getContext().setAuthentication(authentication);
			}
		}

		filterChain.doFilter(request, response);
	}
}
