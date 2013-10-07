/*
 * Copyright 2005-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ldap.config;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.parsing.BeanComponentDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.AbstractBeanDefinitionParser;
import org.springframework.beans.factory.xml.BeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.ldap.pool.PoolExhaustedAction;
import org.springframework.ldap.pool.factory.PoolingContextSource;
import org.springframework.ldap.pool.validation.DefaultDirContextValidator;
import org.springframework.ldap.transaction.compensating.manager.TransactionAwareContextSourceProxy;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import static org.springframework.ldap.config.ParserUtils.NAMESPACE;
import static org.springframework.ldap.config.ParserUtils.getBoolean;
import static org.springframework.ldap.config.ParserUtils.getInt;
import static org.springframework.ldap.config.ParserUtils.getString;

/**
 * @author Mattias Hellborg Arthursson
 */
public class ContextSourceParser implements BeanDefinitionParser {
    private final static String ATT_ANONYMOUS_READ_ONLY = "anonymous-read-only";
    private final static String ATT_AUTHENTICATION_STRATEGY_REF = "authentication-strategy-ref";
    private final static String ATT_BASE = "base";
    private final static String ATT_PASSWORD = "password";
    private final static String ATT_NATIVE_POOLING = "native-pooling";
    private final static String ATT_REFERRAL = "referral";
    private final static String ATT_URL = "url";

    // pooling attributes
    private final static String ATT_MAX_ACTIVE = "max-active";
    private final static String ATT_MAX_TOTAL = "max-total";
    private final static String ATT_MAX_IDLE = "max-idle";
    private final static String ATT_MIN_IDLE = "min-idle";
    private final static String ATT_MAX_WAIT = "max-wait";
    private final static String ATT_WHEN_EXHAUSTED = "when-exhausted";
    private final static String ATT_TEST_ON_BORROW = "test-on-borrow";
    private final static String ATT_TEST_ON_RETURN = "test-on-return";
    private final static String ATT_TEST_WHILE_IDLE = "test-while-idle";
    private final static String ATT_EVICTION_RUN_MILLIS = "eviction-run-interval-millis";
    private final static String ATT_TESTS_PER_EVICTION_RUN = "tests-per-eviction-run";
    private final static String ATT_EVICTABLE_TIME_MILLIS = "min-evictable-time-millis";
    private final static String ATT_VALIDATION_QUERY_BASE = "validation-query-base";
    private final static String ATT_VALIDATION_QUERY_FILTER = "validation-query-filter";
    private final static String ATT_VALIDATION_QUERY_SEARCH_CONTROLS_REF = "validation-query-search-controls-ref";

    private final static String ATT_USERNAME = "username";
    static final String DEFAULT_ID = "contextSource";

    @Override
    public BeanDefinition parse(Element element, ParserContext parserContext) {
        BeanDefinitionBuilder builder = BeanDefinitionBuilder.rootBeanDefinition(LdapContextSource.class);

        String username = element.getAttribute(ATT_USERNAME);
        String password = element.getAttribute(ATT_PASSWORD);
        String url = element.getAttribute(ATT_URL);

        Assert.hasText(username, "username attribute must be specified");
        Assert.hasText(password, "password attribute must be specified");
        Assert.hasText(url, "url attribute must be specified");

        builder.addPropertyValue("userDn", username);
        builder.addPropertyValue("password", password);
        String[] urls = StringUtils.commaDelimitedListToStringArray(url);
        builder.addPropertyValue("urls", urls);
        builder.addPropertyValue("base", getString(element, ATT_BASE, ""));
        builder.addPropertyValue("referral", getString(element, ATT_REFERRAL, null));

        builder.addPropertyValue("anonymousReadOnly", getBoolean(element, ATT_ANONYMOUS_READ_ONLY, false));
        builder.addPropertyValue("pooled", getBoolean(element, ATT_NATIVE_POOLING, false));

        String authStrategyRef = element.getAttribute(ATT_AUTHENTICATION_STRATEGY_REF);
        if(StringUtils.hasText(authStrategyRef)) {
            builder.addPropertyReference("authenticationStrategy", authStrategyRef);
        }

        BeanDefinition targetContextSourceDefinition = builder.getBeanDefinition();
        targetContextSourceDefinition = applyPoolingIfApplicable(targetContextSourceDefinition, element);


        BeanDefinitionBuilder proxyBuilder = BeanDefinitionBuilder.rootBeanDefinition(TransactionAwareContextSourceProxy.class);
        proxyBuilder.addConstructorArgValue(targetContextSourceDefinition);
        AbstractBeanDefinition proxyBeanDefinition = proxyBuilder.getBeanDefinition();

        String id = getString(element, AbstractBeanDefinitionParser.ID_ATTRIBUTE, DEFAULT_ID);
        parserContext.registerBeanComponent(new BeanComponentDefinition(proxyBeanDefinition, id));

        return proxyBeanDefinition;
    }

    private BeanDefinition applyPoolingIfApplicable(BeanDefinition targetContextSourceDefinition, Element element) {
        NodeList poolingChildren = element.getElementsByTagNameNS(NAMESPACE, Elements.POOLING);
        if(poolingChildren.getLength() == 0) {
            return targetContextSourceDefinition;
        }

        Element poolingElement = (Element) poolingChildren.item(0);
        BeanDefinitionBuilder builder = BeanDefinitionBuilder.rootBeanDefinition(PoolingContextSource.class);
        builder.addPropertyValue("contextSource", targetContextSourceDefinition);

        builder.addPropertyValue("maxActive", getInt(poolingElement, ATT_MAX_ACTIVE, 8));
        builder.addPropertyValue("maxTotal", getInt(poolingElement, ATT_MAX_TOTAL, -1));
        builder.addPropertyValue("maxIdle", getInt(poolingElement, ATT_MAX_IDLE, 8));
        builder.addPropertyValue("minIdle", getInt(poolingElement, ATT_MIN_IDLE, 0));
        builder.addPropertyValue("maxWait", getInt(poolingElement, ATT_MAX_WAIT, -1));
        String whenExhausted = getString(poolingElement, ATT_WHEN_EXHAUSTED, PoolExhaustedAction.BLOCK.name());
        builder.addPropertyValue("whenExhaustedAction", PoolExhaustedAction.valueOf(whenExhausted).getValue());

        boolean testOnBorrow = getBoolean(poolingElement, ATT_TEST_ON_BORROW, false);
        boolean testOnReturn = getBoolean(poolingElement, ATT_TEST_ON_RETURN, false);
        boolean testWhileIdle = getBoolean(poolingElement, ATT_TEST_WHILE_IDLE, false);

        if(testOnBorrow || testOnReturn || testWhileIdle) {
            populatePoolValidationProperties(builder, poolingElement, testOnBorrow, testOnReturn, testWhileIdle);
        }

        return builder.getBeanDefinition();
    }

    private void populatePoolValidationProperties(BeanDefinitionBuilder builder, Element element,
                                                  boolean testOnBorrow, boolean testOnReturn, boolean testWhileIdle) {

        builder.addPropertyValue("testOnBorrow", testOnBorrow);
        builder.addPropertyValue("testOnReturn", testOnReturn);
        builder.addPropertyValue("testWhileIdle", testWhileIdle);

        BeanDefinitionBuilder validatorBuilder = BeanDefinitionBuilder.rootBeanDefinition(DefaultDirContextValidator.class);
        validatorBuilder.addPropertyValue("base", getString(element, ATT_VALIDATION_QUERY_BASE, ""));
        validatorBuilder.addPropertyValue("filter",
                getString(element, ATT_VALIDATION_QUERY_FILTER, DefaultDirContextValidator.DEFAULT_FILTER));
        String searchControlsRef = element.getAttribute(ATT_VALIDATION_QUERY_SEARCH_CONTROLS_REF);
        if(StringUtils.hasText(searchControlsRef)) {
            validatorBuilder.addPropertyReference("searchControls", searchControlsRef);
        }
        builder.addPropertyValue("dirContextValidator", validatorBuilder.getBeanDefinition());

        builder.addPropertyValue("timeBetweenEvictionRunsMillis", getInt(element, ATT_EVICTION_RUN_MILLIS, -1));
        builder.addPropertyValue("numTestsPerEvictionRun", getInt(element, ATT_TESTS_PER_EVICTION_RUN, 3));
        builder.addPropertyValue("minEvictableIdleTimeMillis", getInt(element, ATT_EVICTABLE_TIME_MILLIS, 1000 * 60 * 30));
    }

}