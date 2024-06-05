package com.zhengqing.demo;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import com.zhengqing.demo.model.bo.User;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.CreateIndexResponse;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.client.indices.GetIndexResponse;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.SortOrder;
import org.junit.Test;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;

import java.util.Map;

/**
 * <p> es测试 </p>
 *
 * @author zhengqingya
 * @description
 * @date 2024/6/4 20:16
 */
@Slf4j
public class App {

    private static RestHighLevelClient getClient() {
        LoggingSystem.get(LoggingSystem.class.getClassLoader()).setLogLevel("root", LogLevel.INFO);
        return new RestHighLevelClient(
                RestClient.builder(new HttpHost("localhost", 9200, "http"))
                        .setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder.setDefaultCredentialsProvider(new BasicCredentialsProvider() {{
                            setCredentials(AuthScope.ANY, new UsernamePasswordCredentials("elastic", "123456"));
                        }}))
        );
    }

    public static class IndexTest {
        // 查看 http://localhost:9200/user
        final String MAPPING_TEMPLATE = "{\n" +
                "  \"mappings\": {\n" +
                "    \"properties\": {\n" +
                "      \"age\": {\n" +
                "        \"type\": \"long\"\n" +
                "      },\n" +
                "      \"name\": {\n" +
                "        \"type\": \"keyword\"\n" +
                "      },\n" +
                "      \"content\": {\n" +
                "        \"type\": \"text\",\n" +
                "        \"analyzer\": \"ik_smart\",\n" +
                "        \"search_analyzer\": \"ik_smart\"\n" +
                "      },\n" +
                "      \"explain\": {\n" +
                "        \"type\": \"text\",\n" +
                "        \"fields\": {\n" +
                "          \"explain-alias\": {\n" +
                "            \"type\": \"keyword\"\n" +
                "          }\n" +
                "        }\n" +
                "      },\n" +
                "      \"sex\": {\n" +
                "        \"type\": \"keyword\"\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}";

        @Test
        public void create() throws Exception {
            CreateIndexResponse createIndexResponse = getClient().indices().create(
                    new CreateIndexRequest("user3")
                            .source(MAPPING_TEMPLATE, XContentType.JSON),
                    RequestOptions.DEFAULT
            );
            System.out.println(createIndexResponse.isAcknowledged());
        }

        @Test
        public void update() throws Exception {
            GetIndexResponse getIndexResponse = getClient().indices().get(new GetIndexRequest("user2"), RequestOptions.DEFAULT);
            System.out.println(getIndexResponse.getAliases());
            System.out.println(getIndexResponse.getMappings());
            System.out.println(getIndexResponse.getSettings());
        }

        @Test
        public void delete() throws Exception {
            AcknowledgedResponse acknowledgedResponse = getClient().indices().delete(new DeleteIndexRequest("user2"), RequestOptions.DEFAULT);
            System.out.println(acknowledgedResponse.isAcknowledged());
        }
    }

    public static class DocumentTest {
        @Test
        public void create() throws Exception {
            RestHighLevelClient client = getClient();
            IndexRequest request = new IndexRequest();
            request.index("user") // 索引
                    .id("1002") // 如果不设置值的情况下，es会默认生成一个_id值
            ;
            request.source(JSONUtil.toJsonStr(
                    User.builder().name(DateUtil.now())
                            .age(RandomUtil.randomInt(100))
                            .sex(RandomUtil.randomString("男女", 1))
                            .build()
            ), XContentType.JSON);
            IndexResponse response = client.index(request, RequestOptions.DEFAULT);
            System.out.println(JSONUtil.toJsonStr(response));
        }

        @Test
        public void update() throws Exception {
            UpdateRequest request = new UpdateRequest();
            request.index("user").id("1");
            request.doc(JSONUtil.toJsonStr(
                    User.builder()
                            .name(DateUtil.now())
                            .age(58)
                            .build()
            ), XContentType.JSON);
            UpdateResponse response = getClient().update(request, RequestOptions.DEFAULT);
            System.out.println(response);
        }


        @Test
        public void get() throws Exception {
            GetRequest request = new GetRequest().index("user").id("1");
            GetResponse response = getClient().get(request, RequestOptions.DEFAULT);
            System.out.println(response);
        }

        @Test
        public void delete() throws Exception {
            DeleteRequest request = new DeleteRequest().index("user").id("1");
            DeleteResponse response = getClient().delete(request, RequestOptions.DEFAULT);
            System.out.println(response);
        }
    }


    public static class BatchTest {
        @Test
        public void add() throws Exception {
            BulkRequest request = new BulkRequest();
            for (int i = 0; i < 10; i++) {
                request.add(
                        new IndexRequest().index("user").id(String.valueOf(i + 1))
                                .source(
                                        JSONUtil.toJsonStr(
                                                User.builder()
                                                        .name(RandomUtil.randomString("张三李四", 2))
                                                        .age(RandomUtil.randomInt(10))
                                                        .sex(RandomUtil.randomString("男女", 1))
                                                        .content(DateUtil.now() + RandomUtil.randomString("你一定要努力学习，加油！", 5))
                                                        .explain(RandomUtil.randomString("奋斗吧少年，你会是最棒的仔！", 5))
                                                        .build()
                                        ),
                                        XContentType.JSON
                                )
                );
            }
            BulkResponse response = getClient().bulk(request, RequestOptions.DEFAULT);
            System.out.println(JSONUtil.toJsonStr(response));
        }

        @Test
        public void delete() throws Exception {
            BulkRequest request = new BulkRequest();
            for (int i = 0; i < 10; i++) {
                request.add(new DeleteRequest().index("user").id(String.valueOf(i + 1)));
            }
            BulkResponse responses = getClient().bulk(request, RequestOptions.DEFAULT);
            System.out.println(responses);
        }
    }

    public static class AdvancedTest {
        @Test // 条件查询
        public void test() throws Exception {
            // 创建搜索请求对象
            SearchRequest request = new SearchRequest().indices("user");
            // 构建查询的请求体
            SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

            // -----------------------------------------------

            // 全量查询
//            sourceBuilder.query(QueryBuilders.matchAllQuery());

            // 完全匹配 -- =
//            sourceBuilder.query(QueryBuilders.termQuery("age", "68"));

            // 范围查询
//            sourceBuilder.query(
//                    QueryBuilders
//                            .rangeQuery("age")
//                            .gte("30")    // 大于等于
//                            .lte("60")  // 小于等于
//            );

            // 组合查询
            BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery()
                    // filter 过滤 不会计算分值，性能比must高；must会计算分值
//                    .filter(QueryBuilders.termQuery("age", 2)) // termQuery 精准匹配
                    /**
                     * termQuery 精准匹配 text字段类型 时 需要加上`.别名字段`才能查询出 （tips：同时需要建立索引时对该字段通过fields进行多字段配置）
                     * 同字段多type配置
                     * PUT my-index-000001
                     * {
                     *   "mappings": {
                     *     "properties": {
                     *       "explain": {
                     *         "type": "text",
                     *         "fields": {
                     *           "explain-alias": {
                     *             "type":  "keyword"
                     *           }
                     *         }
                     *       }
                     *     }
                     *   }
                     * }
                     */
//                    .filter(QueryBuilders.termQuery("explain.explain-alias", "的斗年仔斗"))
//                    .filter(QueryBuilders.matchQuery("explain", "斗年"))
//                    .filter(QueryBuilders.matchQuery("content", "学习")) // 模糊查询（text字段类型才行） -- 分词后倒排索引查询结果更多
                    .filter(QueryBuilders.matchPhraseQuery("content", "2024-06-05 23:42:05努定！一力")) // 确保搜索词条在文档中的顺序与查询中的顺序一致
//                    .must(QueryBuilders.matchQuery("age", "68")) // must -- and
//                    .mustNot(QueryBuilders.matchQuery("name", "xxx"))  // mustNot -- 排除 !=
//                    .should(QueryBuilders.matchQuery("sex", "男"))  // should -- or
                    ;
            sourceBuilder.query(boolQueryBuilder);


            // 高亮查询
//            sourceBuilder.query(QueryBuilders.matchQuery("name", "努力"));
//            // 构建高亮字段
//            HighlightBuilder highlightBuilder = new HighlightBuilder()
//                    .preTags("<em color='red'>")//设置标签前缀
//                    .postTags("</em>")//设置标签后缀
//                    .field("name");//设置高亮字段
//            // 设置高亮构建对象
//            sourceBuilder.highlighter(highlightBuilder);

            // 最大值查询
//            sourceBuilder.aggregation(AggregationBuilders.max("maxAge").field("age"));

            // 分组 -- Aggregations 中 docCount 记录了分组后的数量
//            sourceBuilder.aggregation(AggregationBuilders.terms("age_groupby").field("age"));

            // 排序 -- 升序
            sourceBuilder.sort("age", SortOrder.ASC);

            // 分页查询
            sourceBuilder.from(0).size(3);
//            sourceBuilder.from(10000).size(3); // 默认限制最大10000，超出报错： from + size must be less than or equal to: [10000] but was [10003].
//            sourceBuilder.trackTotalHits(true).from(10000).size(3); // 网上说 trackTotalHits 设置true 可解决限制，但这里无效

            // -----------------------------------------------

            request.source(sourceBuilder);
            SearchResponse response = getClient().search(request, RequestOptions.DEFAULT);
            // 查询匹配
            SearchHits hits = response.getHits();
            System.out.println("took:" + response.getTook());
            System.out.println("timeout:" + response.isTimedOut());
            System.out.println("total:" + hits.getTotalHits());
            System.out.println("MaxScore:" + hits.getMaxScore());
            System.out.println("Aggregations:" + JSONUtil.toJsonStr(response.getAggregations()));

            System.out.println("hits========>>");
            for (SearchHit hit : hits) {
                //输出每条查询的结果信息
                System.out.println(hit.getSourceAsString());

                // 打印高亮结果
                Map<String, HighlightField> highlightFields = hit.getHighlightFields();
                if (MapUtil.isNotEmpty(highlightFields)) {
                    System.err.println(highlightFields);
                }
            }
            System.out.println("<<========");
        }
    }


}
