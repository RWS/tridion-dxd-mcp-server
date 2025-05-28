package com.sdl.delivery.example.mcp.server;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.sdl.delivery.content.graphql.generated.types.IshRecommendResult;
import com.sdl.delivery.content.graphql.generated.types.IshToc;
import com.sdl.delivery.content.graphql.generated.types.IshTopic;
import com.sdl.delivery.content.graphql.generated.types.Search;
import com.sdl.delivery.content.graphql.generated.types.SearchResultsConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * MCP (Model Context Protocol) Server for Tridion DXD Content Service.
 *
 * <p>This service provides AI tool annotations for interacting with Tridion DXD's GraphQL API
 * to retrieve and search content. It serves as a bridge between AI assistants and the DXD
 * framework, enabling intelligent content discovery and retrieval.</p>
 *
 * <p>The server supports the following operations:</p>
 * <ul>
 *   <li>Retrieving Table of Contents (TOC) for publications</li>
 *   <li>Fetching topic content by publication ID and topic ID</li>
 *   <li>Fetching topic content by publication ID and URL</li>
 *   <li>Searching topics by text terms</li>
 *   <li>Getting topic recommendations based on existing topics</li>
 * </ul>
 *
 * <p>All methods are annotated with {@link Tool} to make them available as AI-callable functions.
 * The service handles GraphQL communication through {@link HttpGraphQlClient}.</p>
 *
 * <p>Responses are typically returned as JSON strings for structured data (TOC, search results,
 * recommendations) or as raw XHTML content for topic bodies.</p>
 *
 * @see org.springframework.ai.tool.annotation.Tool
 * @see org.springframework.graphql.client.HttpGraphQlClient
 * @see com.sdl.delivery.content.graphql.generated.types
 */
@Service
public class McpServer {

    private static final Logger LOG = LoggerFactory.getLogger(McpServer.class);

    private final HttpGraphQlClient graphQLClient;
    private final ObjectMapper objectMapper = new ObjectMapper();


    @Autowired
    public McpServer(WebClient webClient) {
        this.graphQLClient = HttpGraphQlClient.create(webClient);
    }


    /**
     * Gets the Table of Content for a given publication ID.
     *
     * @param publicationId The publication ID
     * @return The topic content (XHTML)
     */
    @Tool(description = "Gets the Table of Content for a given publication ID")
    public String getToc(@ToolParam(description = "The publication ID") final Integer publicationId) {
        try {
            String query = """
                    query ishToc($publicationId: Int!) {
                        ishToc(publicationId: $publicationId) {
                            entries {
                                id
                                tocId
                                url
                                title
                                hasChildren
                                entries {
                                    id
                                    tocId
                                    url
                                    title
                                    hasChildren
                                    entries {
                                        id
                                        tocId
                                        url
                                        title
                                        hasChildren
                                    }
                                }
                            }
                        }
                    }
                    """;

            Map<String, Object> variables = Map.of("publicationId", publicationId);

            return graphQLClient.document(query)
                    .variables(variables)
                    .retrieve("ishToc")
                    .toEntity(IshToc.class)
                    .map(this::processTocResults)
                    .onErrorReturn("Error: [Request failed]")
                    .doOnError(throwable -> LOG.error("Error fetching TOC", throwable))
                    .block();

        } catch (Exception e) {
            LOG.error("Error creating GraphQL request", e);
            return "Error: [" + e.getMessage() + "]";
        }
    }


    private String processTocResults(IshToc ishToc) {
        if (ishToc == null) {
            LOG.warn("No TOC found");
            return "{}";
        }

        LOG.info("Processing TOC with {} entries",
                ishToc.getEntries() != null ? ishToc.getEntries().size() : 0);

        try {
            return objectMapper.writeValueAsString(ishToc);
        } catch (Exception e) {
            LOG.error("Error serializing TOC results", e);
            return "{}";
        }
    }


    /**
     * Get topic content by publication ID and topic ID.
     *
     * @param publicationId The publication ID
     * @param topicId       The topic URL
     * @return The topic content (XHTML)
     */
    @Tool(description = "Get the content for a specific topic given its publication ID and topic ID")
    public String getTopicContentById(@ToolParam(description = "The publication ID") final Integer publicationId,
                                      @ToolParam(description = "The topic ID") final Integer topicId) {
        try {
            String query = """
                    query ishTopicByURL($publicationId: Int!, $topicId: Int!) {
                        ishTopic(publicationId: $publicationId, topicId: $topicId) {
                            __typename
                            publicationId
                            itemId
                            title
                            shortDescription
                            url
                            xhtml
                            ... on IshTaskTopic {
                                body {
                                     steps {
                                        __typename
                                        title
                                        xhtml
                                    }
                                }
                            }
                            links {
                                item {
                                    __typename
                                    publicationId
                                    itemId
                                    title
                                    ... on BinaryComponent {
                                        __typename
                                        publicationId
                                        itemId
                                        variants {
                                            edges {
                                                node {
                                                    binaryId
                                                    downloadUrl
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            relatedLinks {
                                links {
                                    item {
                                        __typename
                                        publicationId
                                        itemId
                                        title
                                        ... on IshGenericTopic {
                                            shortDescription
                                        }
                                    }
                                }
                            }
                        }
                    }
                    """;

            Map<String, Object> variables = Map.of("publicationId", publicationId, "topicId", topicId);

            return graphQLClient.document(query)
                    .variables(variables)
                    .retrieve("ishTopic")
                    .toEntity(IshTopic.class)
                    .map(this::processTopicContent)
                    .onErrorReturn("Error: [Request failed]")
                    .doOnError(throwable -> LOG.error("Error fetching topic content", throwable))
                    .block();

        } catch (Exception e) {
            LOG.error("Error creating GraphQL request", e);
            return "Error: [" + e.getMessage() + "]";
        }
    }


    /**
     * Get topic content by publication ID and URL.
     *
     * @param publicationId The publication ID
     * @param url           The topic URL
     * @return The topic content (XHTML)
     */
    @Tool(description = "Get the content for a specific topic given its publication ID and URL")
    public String getTopicContentByUrl(@ToolParam(description = "The publication ID") final Integer publicationId,
                                       @ToolParam(description = "The topic URL") final String url) {
        try {
            String query = """
                    query ishTopicByURL($publicationId: Int!, $url: String!) {
                        ishTopic(publicationId: $publicationId, url: $url) {
                            __typename
                            publicationId
                            itemId
                            title
                            shortDescription
                            url
                            xhtml
                            ... on IshTaskTopic {
                                body {
                                     steps {
                                        __typename
                                        title
                                        xhtml
                                    }
                                }
                            }
                            links {
                                item {
                                    __typename
                                    publicationId
                                    itemId
                                    title
                                    ... on BinaryComponent {
                                        __typename
                                        publicationId
                                        itemId
                                        variants {
                                            edges {
                                                node {
                                                    binaryId
                                                    downloadUrl
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            relatedLinks {
                                links {
                                    item {
                                        __typename
                                        publicationId
                                        itemId
                                        title
                                        ... on IshGenericTopic {
                                            shortDescription
                                        }
                                    }
                                }
                            }
                        }
                    }
                    """;

            Map<String, Object> variables = Map.of("publicationId", publicationId, "url", url);

            return graphQLClient.document(query)
                    .variables(variables)
                    .retrieve("ishTopic")
                    .toEntity(IshTopic.class)
                    .map(this::processTopicContent)
                    .onErrorReturn("Error: [Request failed]")
                    .doOnError(throwable -> LOG.error("Error fetching topic content", throwable))
                    .block();

        } catch (Exception e) {
            LOG.error("Error creating GraphQL request", e);
            return "Error: [" + e.getMessage() + "]";
        }
    }

    private String processTopicContent(IshTopic ishTopic) {
        if (ishTopic == null) {
            LOG.warn("No topic content found");
            return "{}";
        }

        LOG.info("Processing topic content for item ID: {}", ishTopic.getItemId());

        try {
            return objectMapper.writeValueAsString(ishTopic);
        } catch (Exception e) {
            LOG.error("Error serializing topic content", e);
            return "{}";
        }
    }


    /**
     * Searches all topics for a particular term.
     *
     * @param term The term to search for
     * @return A list of matching topics in JSON format
     */
    @Tool(description = "Search all topics")
    public String searchTopics(@ToolParam(description = "The terms to search for") final String term) {
        try {
            String query = """
                    query searchTopics($term: String!) {
                        search(
                            criteria: {
                                languageField: {
                                    key: "content"
                                    value: $term
                                    language: "english"
                                    strict: true
                                }
                                and: { field: { key: "itemType", value: "page" } }
                            }
                        ) {
                            results(first: 10) {
                                hits
                                edges {
                                    node {
                                        search {
                                            score
                                            id
                                            locale
                                            url
                                            title
                                        }
                                    }
                                }
                            }
                        }
                    }
                    """;

            Map<String, Object> variables = Map.of("term", term);

            return graphQLClient.document(query)
                    .variables(variables)
                    .retrieve("search.results")
                    .toEntity(SearchResultsConnection.class)
                    .map(this::processSearchResults)
                    .onErrorReturn("Error: [Request failed]")
                    .doOnError(throwable -> LOG.error("Error searching topics", throwable))
                    .block();

        } catch (Exception e) {
            LOG.error("Error creating GraphQL request", e);
            return "Error: [" + e.getMessage() + "]";
        }
    }

    private String processSearchResults(SearchResultsConnection searchResultsConnection) {
        if (searchResultsConnection == null || searchResultsConnection.getEdges() == null) {
            LOG.warn("No search results found");
            return "[]";
        }

        LOG.info("Processing {} search results", searchResultsConnection.getEdges().size());

        List<Search> searchResults = searchResultsConnection.getEdges().stream()
                .filter(edge -> edge.getNode() != null && edge.getNode().getSearch() != null)
                .map(edge -> edge.getNode().getSearch())
                .collect(Collectors.toList());

        try {
            return objectMapper.writeValueAsString(searchResults);
        } catch (Exception e) {
            LOG.error("Error serializing search results", e);
            return "[]";
        }
    }


    /**
     * Finds recommended topics given an existing topic.
     *
     * @param topic The topic to get recommendations for in the format 'ish_<publicationId>-<topicId>-16'
     * @return A list of recommended topics in JSON format
     */
    @Tool(description = "Get recommendations for a given topic")
    public String getRecommendations(@ToolParam(description = "The topic to get recommendations for, in the format 'ish_<publicationId>-<topicId>-16'") final String topic) {
        try {
            String query = """
                    query recommendTopics($topic: String!) {
                      ishRecommend(topicId: $topic) {
                        sourceTopic {
                            id
                            url
                            locale
                            title
                        }
                        results {
                            id
                            url
                            locale
                            title
                            publicationId
                            publicationTitle
                        }
                      }
                    }
                    """;

            Map<String, Object> variables = Map.of("topic", topic);

            return graphQLClient.document(query)
                    .variables(variables)
                    .retrieve("ishRecommend")
                    .toEntity(IshRecommendResult.class)
                    .map(this::processRecommendResults)
                    .onErrorReturn("Error: [Request failed]")
                    .doOnError(throwable -> LOG.error("Error looking for recommendations", throwable))
                    .block();

        } catch (Exception e) {
            LOG.error("Error creating GraphQL request", e);
            return "Error: [" + e.getMessage() + "]";
        }
    }

    private String processRecommendResults(IshRecommendResult ishRecommendResult) {
        if (ishRecommendResult == null || ishRecommendResult.getResults() == null) {
            LOG.warn("No recommendation results found");
            return "[]";
        }

        LOG.info("Processing {} recommendation results", ishRecommendResult.getResults().size());

        List<Search> recommendationResults = ishRecommendResult.getResults().stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        try {
            return objectMapper.writeValueAsString(recommendationResults);
        } catch (Exception e) {
            LOG.error("Error serializing recommendation results", e);
            return "[]";
        }
    }
}
