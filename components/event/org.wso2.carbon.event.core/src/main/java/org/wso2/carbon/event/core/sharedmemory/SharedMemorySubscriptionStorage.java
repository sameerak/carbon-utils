/*
 * Copyright (c) 2005-2011, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.event.core.sharedmemory;

import org.wso2.carbon.event.core.subscription.Subscription;
import org.wso2.carbon.event.core.exception.EventBrokerException;
import org.wso2.carbon.event.core.util.EventBrokerConstants;

import javax.cache.Cache;
import javax.cache.CacheConfiguration;
import javax.cache.CacheManager;
import javax.cache.Caching;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;



/**
 * this class is used to keep the details of the subscription storage. Simply this contains
 * maps to keep the subscrition object deatils with the topic details
 */
@SuppressWarnings("serial")
public class SharedMemorySubscriptionStorage implements Serializable {

    private static boolean topicSubscriptionCacheInit =false;
    private static boolean tenantIDInMemorySubscriptionStorageCacheInit = false;
    /**
     * Cache to keep the subscription details with the topics. This is important in finding subscriptions
     * for a pirticular topic when publishing a message to a topic.
     */
    public static Cache<String, SubscriptionContainer> getTopicSubscriptionCache() {
        if (topicSubscriptionCacheInit) {
            return Caching.getCacheManagerFactory()
                    .getCacheManager(EventBrokerConstants.SHARED_MEMORY_CACHE_MANAGER_NAME)
                    .getCache("topicSubscriptionCache");
        } else {
            CacheManager cacheManager = Caching.getCacheManagerFactory()
                    .getCacheManager(EventBrokerConstants.SHARED_MEMORY_CACHE_MANAGER_NAME);
            String cacheName = "topicSubscriptionCache";
            topicSubscriptionCacheInit = true;
            return cacheManager.<String, SubscriptionContainer>createCacheBuilder(cacheName)
                    .setExpiry(CacheConfiguration.ExpiryType.MODIFIED,
                        new CacheConfiguration.Duration(TimeUnit.SECONDS,
                                EventBrokerConstants.SHARED_MEMORY_CACHE_INVALIDATION_TIME))
                    .setExpiry(CacheConfiguration.ExpiryType.ACCESSED,
                        new CacheConfiguration.Duration(TimeUnit.SECONDS,
                                EventBrokerConstants.SHARED_MEMORY_CACHE_INVALIDATION_TIME))
                    .setStoreByValue(false).build();

        }
    }

    public static Cache<String, String> getSubscriptionIDTopicNameCache() {
        if (tenantIDInMemorySubscriptionStorageCacheInit) {
            return Caching.getCacheManagerFactory()
                    .getCacheManager(EventBrokerConstants.SHARED_MEMORY_CACHE_MANAGER_NAME)
                    .getCache("subscriptionIDTopicNameCache");
        } else {
            CacheManager cacheManager = Caching.getCacheManagerFactory()
                    .getCacheManager(EventBrokerConstants.SHARED_MEMORY_CACHE_MANAGER_NAME);
            String cacheName = "subscriptionIDTopicNameCache";
            tenantIDInMemorySubscriptionStorageCacheInit = true;
            return cacheManager.<String, String>createCacheBuilder(cacheName)
                    .setExpiry(CacheConfiguration.ExpiryType.MODIFIED,
                            new CacheConfiguration.Duration(TimeUnit.SECONDS,
                                    EventBrokerConstants.SHARED_MEMORY_CACHE_INVALIDATION_TIME))
                    .setExpiry(CacheConfiguration.ExpiryType.ACCESSED,
                            new CacheConfiguration.Duration(TimeUnit.SECONDS,
                                    EventBrokerConstants.SHARED_MEMORY_CACHE_INVALIDATION_TIME))
                    .setStoreByValue(false).build();

        }
    }

    public SharedMemorySubscriptionStorage() {
    }

    public void addSubscription(Subscription subscription) {
        String topicName = getTopicName(subscription.getTopicName());
        SubscriptionContainer subscriptionsContainer = getTopicSubscriptionCache().get(topicName);

        if (subscriptionsContainer == null){
            subscriptionsContainer = new SubscriptionContainer(topicName);
        }
        
        subscriptionsContainer.getSubscriptionsCache().put(subscription.getId(), subscription);
        getTopicSubscriptionCache().put(topicName, subscriptionsContainer);
        getSubscriptionIDTopicNameCache().put(subscription.getId(), topicName);
        
    }

    public List<Subscription> getMatchingSubscriptions(String topicName) {
        topicName = getTopicName(topicName);
        List<Subscription> subscriptions = new ArrayList<Subscription>();

        List<String> matchingTopicNames = getTopicMatchingNames(topicName);
        for (String matchingTopicName : matchingTopicNames){
            if (getTopicSubscriptionCache().get(matchingTopicName) != null){
                SubscriptionContainer matchingContainer = getTopicSubscriptionCache().get(matchingTopicName);
                Iterator<String> keysOfSubscription = matchingContainer.getSubscriptionsCache().keys();
                
                while(keysOfSubscription.hasNext()) {
                	String key = keysOfSubscription.next();
                	subscriptions.add(matchingContainer.getSubscriptionsCache().get(key));
                }
            }
        }

        return subscriptions;
    }

    public void unSubscribe(String subscriptionID) throws EventBrokerException {
        String topicName = getTopicName(getSubscriptionIDTopicNameCache().get(subscriptionID));

        if (topicName == null){
            throw new EventBrokerException("Subscription with ID " + subscriptionID + " does not exits");
        }

        SubscriptionContainer subscriptionContainer = getTopicSubscriptionCache().get(topicName);

        if (subscriptionContainer == null){
            throw new EventBrokerException("Subscription with ID " + subscriptionID + " does not exits");
        }

        subscriptionContainer.getSubscriptionsCache().remove(subscriptionID);
        getSubscriptionIDTopicNameCache().remove(subscriptionID);
    }

    public void renewSubscription(Subscription subscription) throws EventBrokerException {
        String topicName = getTopicName(subscription.getTopicName());
        SubscriptionContainer subscriptionContainer = getTopicSubscriptionCache().get(topicName);

        if (subscriptionContainer == null){
            throw new EventBrokerException("There is no subscriptions with topic " + topicName);
        }

        Subscription existingSubscription = subscriptionContainer.getSubscriptionsCache().get(subscription.getId());

        if (existingSubscription == null){
            throw new EventBrokerException("There is no subscription with subscription id " + subscription.getId());
        }

        existingSubscription.setExpires(subscription.getExpires());
        existingSubscription.setProperties(subscription.getProperties());

        String val ;
        if((val = subscription.getProperties().get("notVerfied")) == null) {
            getSubscriptionIDTopicNameCache().put(subscription.getId()+"-notVerfied", "false");
        } else {
            if("true".equalsIgnoreCase(val)) {
                getSubscriptionIDTopicNameCache().put(subscription.getId()+"-notVerfied", "true");
            } else {
                getSubscriptionIDTopicNameCache().put(subscription.getId()+"-notVerfied", "false");
            }
        }

    }

    private List<String> getTopicMatchingNames(String topicName) {
        List<String> matchingTopicNames = new ArrayList<String>();

        if (topicName.equals("/")) {
            matchingTopicNames.add("/#");
        } else {
            String currentTopicName = "";
            String[] topicParts = topicName.split("/");
            int i = 0; // the first part if the split parts are "" since always topics start with /
            while (i < (topicParts.length)) {
                currentTopicName = currentTopicName + topicParts[i] + "/";
                matchingTopicNames.add(currentTopicName + "#");
                if (i == (topicParts.length - 1)||i == (topicParts.length - 2)) {
                    matchingTopicNames.add(currentTopicName + "*");
                }
                i++;
            }
        }
        matchingTopicNames.add(topicName);
        return matchingTopicNames;
    }

    private String getTopicName(String topicName){
        if (!topicName.startsWith("/")){
            topicName = "/" + topicName;
        }

        if (topicName.endsWith("/") && (topicName.length() != 1)){
            topicName = topicName.substring(0, topicName.lastIndexOf("/"));
        }
        return topicName;
    }
}
