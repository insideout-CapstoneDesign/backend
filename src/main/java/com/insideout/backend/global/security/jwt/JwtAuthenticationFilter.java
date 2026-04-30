package com.insideout.backend.global.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
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
import org.springframework.util.StringUtils;
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

			try {
				Claims claims = jwtTokenProvider.getValidatedClaims(token);
				String tokenType = claims.get("tokenType", String.class);
				String subject = claims.getSubject();

				if (ACCESS_TOKEN_TYPE.equals(tokenType)
					&& StringUtils.hasText(subject)
					&& SecurityContextHolder.getContext().getAuthentication() == null) {

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
			} catch (JwtException | IllegalArgumentException ignored) {
				// 유효하지 않은 토큰은 인증을 세팅하지 않고 다음 필터로 진행한다.
			}
		}

		filterChain.doFilter(request, response);
	}
}
