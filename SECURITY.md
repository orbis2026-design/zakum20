# Security Policy

**Project:** Zakum  
**Version:** 0.1.0-SNAPSHOT  
**Last Updated:** February 18, 2026

---

## Supported Versions

| Version | Supported | End of Support |
|---------|-----------|----------------|
| 0.1.x (SNAPSHOT) | ✅ Yes | In Development |

**Note:** Production releases will follow semantic versioning with defined support timelines.

---

## Reporting a Vulnerability

### **DO NOT** Create Public Issues for Security Vulnerabilities

Security vulnerabilities should be reported privately to allow time for a fix before public disclosure.

### Reporting Process

1. **Email:** Send details to `security@example.com`
2. **Subject:** `[SECURITY] Zakum Vulnerability Report`
3. **Include:**
   - Description of the vulnerability
   - Steps to reproduce
   - Potential impact
   - Suggested fix (if any)
   - Your contact information

### Response Timeline

- **Acknowledgment:** Within 48 hours
- **Initial Assessment:** Within 7 days
- **Fix Timeline:** Depends on severity (see below)
- **Public Disclosure:** After fix is released + 30 days

### Severity Levels

| Severity | Response Time | Fix Timeline |
|----------|--------------|--------------|
| **Critical** | Immediate | 24-72 hours |
| **High** | 24 hours | 1 week |
| **Medium** | 72 hours | 2-4 weeks |
| **Low** | 1 week | Next release |

---

## Security Measures

### Code Security

#### Static Analysis
- **CodeQL:** Automated security scanning on every commit
- **OWASP Dependency Check:** Weekly vulnerability scans
- **Manual Reviews:** Security-focused code reviews

#### Build Security
- **Reproducible Builds:** Deterministic build process
- **Dependency Verification:** Checksums validated
- **No Dynamic Code Loading:** No runtime code execution

### Runtime Security

#### Input Validation
- **All Player Input:** Validated and sanitized
- **Command Arguments:** Type-checked and range-validated
- **Configuration:** Strict YAML parsing with validation
- **Database Queries:** Parameterized queries only (no SQL injection)

#### Authentication & Authorization
- **Permission Checks:** Enforced on all admin commands
- **Session Management:** Secure session handling
- **API Authentication:** API keys for external integrations

#### Data Protection
- **Password Storage:** Never store passwords in config
- **Sensitive Data:** API keys encrypted at rest
- **Player Data:** UUID-based, GDPR-compliant storage
- **Audit Logging:** All admin actions logged

### Network Security

#### HTTP Clients
- **Timeouts:** All requests have timeouts
- **TLS:** HTTPS required for external APIs
- **Certificate Validation:** Strict certificate checking
- **Rate Limiting:** Built-in rate limiting

#### Database
- **Prepared Statements:** All queries use parameterization
- **Connection Pooling:** HikariCP with secure configuration
- **Least Privilege:** Database user has minimal permissions
- **Encryption:** TLS for database connections (optional)

---

## Known Security Considerations

### Database Credentials

**Issue:** Database credentials stored in plain text in config.yml

**Mitigation:**
- File permissions (chmod 600)
- Separate database user with limited permissions
- Consider environment variables for production

**Future:** Encrypted credential storage planned

### Plugin Dependencies

**Issue:** Third-party plugins may introduce vulnerabilities

**Mitigation:**
- Bridge modules isolated
- Each bridge optional
- Fail-safe on bridge errors

### Redis Connections

**Issue:** Redis connections may not use authentication

**Mitigation:**
- Configure Redis AUTH
- Use firewall rules
- Redis URI supports password

---

## Security Best Practices

### For Server Administrators

1. **Keep Updated**
   - Update Zakum regularly
   - Update Paper server
   - Update Java JVM

2. **Secure Configuration**
   - Change default credentials
   - Use strong database passwords
   - Enable TLS for external connections
   - Restrict file permissions

3. **Monitor Logs**
   - Review logs regularly
   - Alert on errors
   - Track admin actions

4. **Backup Regularly**
   - Automated backups
   - Test restore procedures
   - Secure backup storage

5. **Principle of Least Privilege**
   - Minimal database permissions
   - Restricted file access
   - Limited API access

### For Plugin Developers

1. **Validate Input**
   - Never trust player input
   - Validate all parameters
   - Use type-safe APIs

2. **Use Async Operations**
   - Never block main thread
   - Use CompletableFuture
   - Handle exceptions properly

3. **Follow API Guidelines**
   - Use published APIs only
   - Don't access internals
   - Check return values

4. **Error Handling**
   - Catch all exceptions
   - Log errors appropriately
   - Fail gracefully

---

## Vulnerability Disclosure Policy

### Our Commitment

- We will acknowledge your report within 48 hours
- We will provide regular updates on progress
- We will credit you in security advisories (if desired)
- We will not take legal action for good-faith research

### Responsible Disclosure

Please:
- Allow us time to fix before public disclosure
- Don't exploit vulnerabilities
- Don't access other users' data
- Don't perform DoS attacks

### Hall of Fame

Security researchers who responsibly disclose vulnerabilities:
- (None yet - be the first!)

---

## Security Checklist

### Development
- [ ] Code review by at least one other developer
- [ ] Static analysis passes (CodeQL)
- [ ] No high/critical vulnerabilities (OWASP)
- [ ] Unit tests include security test cases
- [ ] Input validation for all user input
- [ ] SQL injection prevention verified
- [ ] XSS prevention in text output

### Deployment
- [ ] Secure file permissions (config files 600)
- [ ] Database user has minimal permissions
- [ ] TLS enabled for external connections
- [ ] Monitoring configured
- [ ] Backup system operational
- [ ] Incident response plan documented

---

## Incident Response

### If You Discover a Vulnerability

1. **Stop:** Don't exploit or share publicly
2. **Document:** Capture reproduction steps
3. **Report:** Email security@example.com
4. **Wait:** Allow time for fix

### If We Discover a Vulnerability

1. **Assess:** Determine severity and impact
2. **Fix:** Develop and test patch
3. **Notify:** Inform affected users privately
4. **Release:** Deploy fix quickly
5. **Disclose:** Public advisory after fix deployed

---

## Security Contacts

- **Security Email:** security@example.com
- **GPG Key:** [Available on keyserver]
- **Discord:** Join for general discussion (not for vulnerability reports)
- **GitHub:** Use Security Advisories for coordinated disclosure

---

## Compliance

### GDPR Compliance

Zakum is designed with GDPR compliance in mind:
- **Data Minimization:** Collect only necessary data
- **Right to Erasure:** Admin commands to delete player data
- **Data Portability:** Export player data functionality
- **Consent:** Clear terms of service
- **Audit Logging:** Track all data access

### Java Security

- **Java 21:** Required minimum version
- **Security Updates:** Follow Oracle's security bulletin
- **JVM Arguments:** Recommended secure defaults provided

---

## Security Audit History

| Date | Type | Findings | Status |
|------|------|----------|--------|
| 2026-02-18 | Initial Development | - | ✅ In Progress |

Future audits will be documented here.

---

## Additional Resources

- [OWASP Top 10](https://owasp.org/www-project-top-ten/)
- [CWE Top 25](https://cwe.mitre.org/top25/)
- [Paper Security](https://docs.papermc.io/paper/admin/security)
- [Minecraft Server Security](https://www.spigotmc.org/wiki/server-security/)

---

## Updates to This Policy

This security policy is reviewed and updated regularly. Check commit history for changes.

**Last Review:** February 18, 2026  
**Next Review:** Quarterly

---

**Thank you for helping keep Zakum and its users safe!**

