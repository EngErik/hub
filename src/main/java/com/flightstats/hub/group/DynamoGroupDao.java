package com.flightstats.hub.group;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.model.*;
import com.flightstats.hub.app.HubServices;
import com.flightstats.hub.dao.dynamo.DynamoUtils;
import com.google.common.util.concurrent.AbstractIdleService;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DynamoGroupDao {
    private final static Logger logger = LoggerFactory.getLogger(DynamoGroupDao.class);

    private final AmazonDynamoDBClient dbClient;
    private final DynamoUtils dynamoUtils;

    @Inject
    public DynamoGroupDao(AmazonDynamoDBClient dbClient, DynamoUtils dynamoUtils) {
        this.dbClient = dbClient;
        this.dynamoUtils = dynamoUtils;
        HubServices.register(new DynamoGroupDaoInit());
    }

    private class DynamoGroupDaoInit extends AbstractIdleService {
        @Override
        protected void startUp() throws Exception {
            initialize();
        }

        @Override
        protected void shutDown() throws Exception { }
    }

    public void initialize() {
        CreateTableRequest request = new CreateTableRequest()
                .withTableName(getTableName())
                .withAttributeDefinitions(new AttributeDefinition("name", ScalarAttributeType.S))
                .withKeySchema(new KeySchemaElement("name", KeyType.HASH))
                .withProvisionedThroughput(new ProvisionedThroughput(50L, 10L));
        dynamoUtils.createTable(request);
    }

    public Group upsertGroup(Group group) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("name", new AttributeValue(group.getName()));
        item.put("callbackUrl", new AttributeValue(group.getCallbackUrl()));
        item.put("channelUrl", new AttributeValue(group.getChannelUrl()));
        item.put("transactional", new AttributeValue(Boolean.toString(group.isTransactional())));
        dbClient.putItem(getTableName(), item);
        return group;
    }

    public Group getGroup(String name) {
        HashMap<String, AttributeValue> keyMap = new HashMap<>();
        keyMap.put("name", new AttributeValue(name));
        try {
            GetItemResult result = dbClient.getItem(getTableName(), keyMap);
            if (result.getItem() == null) {
                return null;
            }
            return mapItem(result.getItem());
        } catch (ResourceNotFoundException e) {
            logger.info("group not found " + name + " " + e.getMessage());
            return null;
        }
    }

    private Group mapItem(Map<String, AttributeValue> item) {
        return Group.builder()
                .name(item.get("name").getS())
                .callbackUrl(item.get("callbackUrl").getS())
                .channelUrl(item.get("channelUrl").getS())
                .transactional(Boolean.parseBoolean(item.get("transactional").getS()))
                .build();
    }

    public Iterable<Group> getGroups() {
        List<Group> configurations = new ArrayList<>();

        ScanResult result = dbClient.scan(new ScanRequest(getTableName()));
        mapItems(configurations, result);

        while (result.getLastEvaluatedKey() != null) {
            new ScanRequest(getTableName()).setExclusiveStartKey(result.getLastEvaluatedKey());
            result = dbClient.scan(new ScanRequest(getTableName()));
            mapItems(configurations, result);
        }

        return configurations;
    }

    private void mapItems(List<Group> configurations, ScanResult result) {
        for (Map<String, AttributeValue> item : result.getItems()){
            configurations.add(mapItem(item));
        }
    }

    public void delete(String name) {
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("name", new AttributeValue(name));
        dbClient.deleteItem(new DeleteItemRequest(getTableName(), key));
    }

    public String getTableName() {
        return dynamoUtils.getTableName("GroupConfig");
    }
}
