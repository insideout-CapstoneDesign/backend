package com.insideout.backend.global.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

	private static final String TOKEN_TYPE_CLAIM = "tokenType";
	private static final String ACCESS = "access";
	private static final String REFRESH = "refresh";

	private final SecretKey key;
	private final long accessTokenExpirationMs;
	private final long refreshTokenExpirationMs;

	public JwtTokenProvider(
		@Value("${security.jwt.secret}") String secret,
		@Value("${security.jwt.access-token-expiration-ms}") long accessTokenExpirationMs,
		@Value("${security.jwt.refresh-token-expiration-ms}") long refreshTokenExpirationMs
	) {
		this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
		this.accessTokenExpirationMs = accessTokenExpirationMs;
		this.refreshTokenExpirationMs = refreshTokenExpirationMs;
	}

	public String generateAccessToken(String subject) {
		return generateToken(subject, accessTokenExpirationMs, ACCESS);
	}

	public String generateRefreshToken(String subject) {
		return generateToken(subject, refreshTokenExpirationMs, REFRESH);
	}

	public boolean validateToken(String token) {
		try {
			parseClaims(token);
			return true;
		} catch (JwtException | IllegalArgumentException e) {
			return false;
		}
	}

	public String getSubject(String token) {
		return parseClaims(token).getSubject();
	}

	public String getTokenType(String token) {
		return parseClaims(token).get(TOKEN_TYPE_CLAIM, String.class);
	}

	private String generateToken(String subject, long expirationMs, String tokenType) {
		Date now = new Date();
		Date expiry = new Date(now.getTime() + expirationMs);

		return Jwts.builder()
			.subject(subject)
			.issuedAt(now)
			.expiration(expiry)
			.claim(TOKEN_TYPE_CLAIM, tokenType)
			.signWith(key)
			.compact();
	}

	private Claims parseClaims(String token) {
		return Jwts.parser()
			.verifyWith(key)
			.build()
			.parseSignedClaims(token)
			.getPayload();
	}
}
