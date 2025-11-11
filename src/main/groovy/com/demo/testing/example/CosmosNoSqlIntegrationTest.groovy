package com.demo.testing.example

import com.demo.testing.base.IntegrationTestBaseCosmosNoSqlSpec
import spock.lang.Specification

/**
 * Example Integration Test for Azure Cosmos DB (NoSQL API)
 *
 * This test demonstrates how to use the IntegrationTestBaseCosmosNoSqlSpec base class
 * to run tests against Azure Cosmos DB NoSQL using the Emulator.
 *
 * Features:
 * - Cosmos DB Emulator starts automatically in Docker
 * - cosmosNoSqlUtils provides convenient methods for CRUD operations
 * - SQL queries are supported
 * - Database and container management
 * - Document operations (Create, Read, Update, Delete)
 *
 * Prerequisites:
 * - Docker must be installed and running
 * - Sufficient disk space for Cosmos Emulator (requires ~3GB)
 * - 4GB+ RAM available
 *
 * Dependencies Required:
 * - com.azure:azure-cosmos
 * - org.testcontainers:testcontainers
 * - org.spockframework:spock-core
 */
class CosmosNoSqlIntegrationTest extends IntegrationTestBaseCosmosNoSqlSpec {

    def "test cosmos nosql connectivity"() {
        given: "Cosmos NoSQL utilities are initialized"
        cosmosNoSqlUtils != null
        cosmosEndpoint != null
        cosmosKey != null

        when: "we create a client and verify connectivity"
        def client = cosmosNoSqlUtils.createClient()

        then: "the client should be created successfully"
        client != null
        cosmosEndpoint.contains("https://")
        cosmosKey != null
        cosmosKey.length() > 0
    }

    def "test create database and container"() {
        given: "Cosmos NoSQL utils are ready"
        cosmosNoSqlUtils != null

        when: "we create a database and container"
        def database = cosmosNoSqlUtils.getOrCreateDatabase("testdb", 400)
        def container = cosmosNoSqlUtils.getOrCreateContainer("testdb", "testcontainer", "/id")

        then: "both should be created successfully"
        database != null
        database.getId() == "testdb"
        container != null
        container.getId() == "testcontainer"
    }

    def "test insert and read document"() {
        given: "Database and container are ready"
        cosmosNoSqlUtils.getOrCreateDatabase("users_db", 400)
        cosmosNoSqlUtils.getOrCreateContainer("users_db", "users", "/id")

        when: "we insert a document"
        def testDoc = [
            id: "user1",
            name: "John Doe",
            email: "john@example.com",
            city: "New York",
            country: "USA"
        ]
        cosmosNoSqlUtils.insertDocument("users_db", "users", testDoc, "user1")

        and: "we read it back"
        def readDoc = cosmosNoSqlUtils.readDocument("users_db", "users", "user1", "user1")

        then: "the document should be readable"
        readDoc != null
        readDoc.name == "John Doe"
        readDoc.email == "john@example.com"
    }

    def "test update document"() {
        given: "Document exists in container"
        cosmosNoSqlUtils.getOrCreateDatabase("users_db", 400)
        cosmosNoSqlUtils.getOrCreateContainer("users_db", "users", "/id")

        def originalDoc = [
            id: "user2",
            name: "Jane Smith",
            email: "jane@example.com"
        ]
        cosmosNoSqlUtils.insertDocument("users_db", "users", originalDoc, "user2")

        when: "we update the document"
        def updatedDoc = [
            id: "user2",
            name: "Jane Smith",
            email: "jane.smith@example.com",
            department: "Engineering"
        ]
        cosmosNoSqlUtils.updateDocument("users_db", "users", "user2", updatedDoc, "user2")

        and: "we read the updated document"
        def readDoc = cosmosNoSqlUtils.readDocument("users_db", "users", "user2", "user2")

        then: "the document should reflect the updates"
        readDoc.email == "jane.smith@example.com"
        readDoc.department == "Engineering"
    }

    def "test delete document"() {
        given: "Document exists in container"
        cosmosNoSqlUtils.getOrCreateDatabase("users_db", 400)
        cosmosNoSqlUtils.getOrCreateContainer("users_db", "users", "/id")

        def testDoc = [
            id: "user3",
            name: "Bob Wilson",
            email: "bob@example.com"
        ]
        cosmosNoSqlUtils.insertDocument("users_db", "users", testDoc, "user3")

        when: "we delete the document"
        cosmosNoSqlUtils.deleteDocument("users_db", "users", "user3", "user3")

        then: "attempting to read should fail"
        def exception = thrown(RuntimeException)
        exception.message.contains("Failed to read document")
    }

    def "test query documents with SQL"() {
        given: "Multiple documents in container"
        cosmosNoSqlUtils.getOrCreateDatabase("products_db", 400)
        cosmosNoSqlUtils.getOrCreateContainer("products_db", "products", "/id")

        def products = [
            [id: "prod1", name: "Laptop", category: "Electronics", price: 999.99],
            [id: "prod2", name: "Mouse", category: "Electronics", price: 29.99],
            [id: "prod3", name: "Desk", category: "Furniture", price: 299.99],
            [id: "prod4", name: "Chair", category: "Furniture", price: 199.99]
        ]

        products.each { product ->
            cosmosNoSqlUtils.insertDocument("products_db", "products", product, product.id)
        }

        when: "we query for electronics"
        def results = cosmosNoSqlUtils.queryDocuments(
            "products_db",
            "products",
            "SELECT * FROM c WHERE c.category = 'Electronics'"
        )

        then: "we should get only electronics"
        results.size() == 2
        results.any { it.name == "Laptop" }
        results.any { it.name == "Mouse" }
    }

    def "test query with ordering"() {
        given: "Multiple products with prices"
        cosmosNoSqlUtils.getOrCreateDatabase("store_db", 400)
        cosmosNoSqlUtils.getOrCreateContainer("store_db", "items", "/id")

        def items = [
            [id: "item1", name: "Item A", price: 50],
            [id: "item2", name: "Item B", price: 30],
            [id: "item3", name: "Item C", price: 100]
        ]

        items.each { item ->
            cosmosNoSqlUtils.insertDocument("store_db", "items", item, item.id)
        }

        when: "we query with order by price ascending"
        def results = cosmosNoSqlUtils.queryDocuments(
            "store_db",
            "items",
            "SELECT * FROM c ORDER BY c.price ASC"
        )

        then: "results should be ordered by price"
        results.size() == 3
        results[0].price == 30
        results[1].price == 50
        results[2].price == 100
    }

    def "test connection details are correctly configured"() {
        expect: "Connection details are set"
        cosmosEndpoint != null
        cosmosEndpoint.startsWith("https://")
        cosmosKey != null
        cosmosKey.length() > 0
        cosmosNoSqlUtils != null

        and: "Endpoint contains expected pattern"
        cosmosEndpoint.contains("8081") || cosmosEndpoint.contains("localhost") || cosmosEndpoint.contains("127.0.0.1")
    }

    def "test multiple containers in same database"() {
        when: "Database with multiple containers"
        cosmosNoSqlUtils.getOrCreateDatabase("multi_container_db", 400)
        def container1 = cosmosNoSqlUtils.getOrCreateContainer("multi_container_db", "container1", "/id")
        def container2 = cosmosNoSqlUtils.getOrCreateContainer("multi_container_db", "container2", "/docId")

        then: "both containers should be created"
        container1 != null
        container2 != null
        container1.getId() != container2.getId()
    }

    def "test batch document operations"() {
        given: "Empty container"
        cosmosNoSqlUtils.getOrCreateDatabase("batch_db", 400)
        cosmosNoSqlUtils.getOrCreateContainer("batch_db", "batch_col", "/id")

        when: "we insert multiple documents"
        (1..5).each { i ->
            def doc = [
                id: "batch_${i}",
                type: "batch_test",
                index: i
            ]
            cosmosNoSqlUtils.insertDocument("batch_db", "batch_col", doc, "batch_${i}")
        }

        and: "we query all batch documents"
        def results = cosmosNoSqlUtils.queryDocuments(
            "batch_db",
            "batch_col",
            "SELECT * FROM c WHERE c.type = 'batch_test'"
        )

        then: "all documents should be retrieved"
        results.size() == 5
    }
}

