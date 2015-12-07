package org.zalando.github.zmon.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.social.security.SocialUser;
import org.springframework.social.security.SocialUserDetails;
import org.springframework.social.security.SocialUserDetailsService;

public class SimpleSocialUserDetailsService implements SocialUserDetailsService {

    private final Logger log = LoggerFactory.getLogger(SimpleSocialUserDetailsService.class);

    private UserDetailsService userDetailsService;

    public SimpleSocialUserDetailsService(final UserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    @Override
    public SocialUserDetails loadUserByUserId(final String username) throws UsernameNotFoundException,
        DataAccessException {

        try {
            log.info("LOAD USER : {}", username);

            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            return new SocialUser(userDetails.getUsername(), "password", AuthorityUtils.createAuthorityList("USER"));
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }
}
