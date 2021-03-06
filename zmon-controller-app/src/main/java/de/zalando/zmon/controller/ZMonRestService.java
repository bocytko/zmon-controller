package de.zalando.zmon.controller;

import java.io.IOException;
import java.io.Writer;

import java.net.URISyntaxException;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.zalando.zmon.exception.ZMonException;
import de.zalando.zmon.persistence.GrafanaDashboardSprocService;
import de.zalando.zmon.security.permission.DefaultZMonPermissionService;

import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;

import org.kairosdb.client.HttpClient;
import org.kairosdb.client.builder.AggregatorFactory;
import org.kairosdb.client.builder.DataPoint;
import org.kairosdb.client.builder.QueryBuilder;
import org.kairosdb.client.builder.QueryMetric;
import org.kairosdb.client.builder.TimeUnit;
import org.kairosdb.client.builder.grouper.TagGrouper;
import org.kairosdb.client.response.Queries;
import org.kairosdb.client.response.QueryResponse;
import org.kairosdb.client.response.Results;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.stereotype.Controller;

import org.springframework.web.bind.annotation.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.LongNode;

import com.google.common.collect.Lists;

import de.zalando.zmon.appconfig.KairosDBConfig;
import de.zalando.zmon.domain.CheckDefinition;
import de.zalando.zmon.domain.CheckHistoryGroupResult;
import de.zalando.zmon.domain.CheckHistoryResult;
import de.zalando.zmon.domain.CheckResults;
import de.zalando.zmon.domain.ExecutionStatus;
import de.zalando.zmon.rest.domain.*;
import de.zalando.zmon.service.ZMonService;
import org.kairosdb.client.builder.DataFormatException;
import org.kairosdb.client.response.grouping.TagGroupResult;

@Controller
@RequestMapping(value="/rest")
public class ZMonRestService extends AbstractZMonController {

    private static final Logger LOG = LoggerFactory.getLogger(ZMonRestService.class);

    @Autowired
    private ZMonService service;

    @Autowired
    private KairosDBConfig kairosDBConfig;

    @RequestMapping(value = "/status", method = RequestMethod.GET)
    public ResponseEntity<ExecutionStatus> getStatus() {
        return new ResponseEntity<>(service.getStatus(), HttpStatus.OK);
    }

    @RequestMapping(value = "/allTeams", method = RequestMethod.GET)
    public ResponseEntity<List<String>> getAllTeams() {
        return new ResponseEntity<>(service.getAllTeams(), HttpStatus.OK);
    }

    @RequestMapping(value = "/checkDefinitions", method = RequestMethod.GET)
    public ResponseEntity<List<CheckDefinition>> getAllCheckDefinitions(
            @RequestParam(value = "team", required = false) final Set<String> teams) {
        final List<CheckDefinition> defs = teams == null ? service.getCheckDefinitions(null).getCheckDefinitions()
                                                         : service.getCheckDefinitions(null, teams);

        return new ResponseEntity<>(defs, HttpStatus.OK);
    }

    @RequestMapping(value = "/checkDefinition", method = RequestMethod.GET)
    public ResponseEntity<CheckDefinition> getCheckDefinition(
            @RequestParam(value = "check_id", required = true) final int checkId) {

        final List<CheckDefinition> checkDefinitions = service.getCheckDefinitions(null, Lists.newArrayList(checkId));
        if (checkDefinitions.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        return new ResponseEntity<>(checkDefinitions.get(0), HttpStatus.OK);
    }

    @RequestMapping(value = "/checkResults", method = RequestMethod.GET)
    public ResponseEntity<List<CheckResults>> getCheckResults(
            @RequestParam(value = "check_id", required = true) final int checkId,
            @RequestParam(value = "entity", required = false) final String entity,
            @RequestParam(value = "limit", defaultValue = "20") final int limit) {

        return new ResponseEntity<>(service.getCheckResults(checkId, entity, limit), HttpStatus.OK);
    }

    @RequestMapping(value = "checkResultsChart", method = RequestMethod.GET)
    public ResponseEntity<CheckChartResult> getChartResults(
            @RequestParam(value = "check_id", required = true) final int checkId,
            @RequestParam(value = "entity", required = false) final String entity,
            @RequestParam(value = "limit", defaultValue = "20") final int limit) {
        return new ResponseEntity<>(service.getChartResults(checkId, entity, limit), HttpStatus.OK);
    }

    @RequestMapping(value = "/checkAlertResults", method = RequestMethod.GET)
    public ResponseEntity<List<CheckResults>> getCheckAlertResults(
            @RequestParam(value = "alert_id", required = true) final int alertId,
            @RequestParam(value = "limit", defaultValue = "20") final int limit) {
        return new ResponseEntity<>(service.getCheckAlertResults(alertId, limit), HttpStatus.OK);
    }

    @RequestMapping(value = "/entityProperties", method = RequestMethod.GET)
    public ResponseEntity<JsonNode> getEntityProperties() {
        return new ResponseEntity<>(service.getEntityProperties(), HttpStatus.OK);
    }

    @ResponseBody
    @RequestMapping(value = "/kairosDBPost", method = RequestMethod.POST, produces = "application/json")
    public void kairosDBPost(@RequestBody(required = true) final JsonNode node, final Writer writer,
            final HttpServletResponse response) throws IOException {

        response.setContentType("application/json");

        if ( !kairosDBConfig.isEnabled() ) {
            writer.write("");
            return;
        }

        final Executor executor = Executor.newInstance();

        final String kairosDBURL = "http://" + kairosDBConfig.getHost() + ":" + kairosDBConfig.getPort()
                + "/api/v1/datapoints/query";

        final String r = executor.execute(Request.Post(kairosDBURL).useExpectContinue().bodyString(node.toString(),
                                         ContentType.APPLICATION_JSON)).returnContent().asString();

        writer.write(r);
    }

    @ResponseBody
    @RequestMapping(value = "/kairosDBtags", method = RequestMethod.POST, produces = "application/json")
    public void kairosDBtags(@RequestBody(required = true) final JsonNode node, final Writer writer,
                             final HttpServletResponse response) throws IOException {

        response.setContentType("application/json");

        if ( !kairosDBConfig.isEnabled() ) {
            writer.write("");
            return;
        }

        final Executor executor = Executor.newInstance();

        final String kairosDBURL = "http://" + kairosDBConfig.getHost() + ":" + kairosDBConfig.getPort()
                + "/api/v1/datapoints/query/tags";

        final String r = executor.execute(Request.Post(kairosDBURL).useExpectContinue().bodyString(node.toString(),
                ContentType.APPLICATION_JSON)).returnContent().asString();

        writer.write(r);
    }

    @ResponseBody
    @RequestMapping(value = "/kairosDBmetrics", method = RequestMethod.GET, produces = "application/json")
    public void kairosDBmetrics(final Writer writer, final HttpServletResponse response) throws IOException {

        response.setContentType("application/json");

        if ( !kairosDBConfig.isEnabled() ) {
            writer.write("");
            return;
        }

        final String kairosDBURL = "http://" + kairosDBConfig.getHost() + ":" + kairosDBConfig.getPort()
                + "/api/v1/metricnames";

        final String r = Request.Get(kairosDBURL).useExpectContinue().execute().returnContent().asString();

        writer.write(r);
    }

    @RequestMapping(value = "/retrieveCheckStatistics", method = RequestMethod.GET)
    public ResponseEntity<CheckHistoryResult> retrieveCheckStatistics(
            @RequestParam(value = "check_id", required = true) final int checkId,
            @RequestParam(value = "entity_id", required = true) final String entityId,
            @RequestParam(value = "days", required = false, defaultValue = "0") final int days,
            @RequestParam(value = "hours", required = false, defaultValue = "0") final int hours,
            @RequestParam(value = "days_end", required = false, defaultValue = "0") final int daysEnd,
            @RequestParam(value = "hours_end", required = false, defaultValue = "0") final int hoursEnd,
            @RequestParam(value = "aggregate", required = false, defaultValue = "30") final int aggregate,
            @RequestParam(value = "aggregate_unit", required = false, defaultValue = "minutes") final String aggregateUnit,
            @RequestParam(value = "start_date", required = false) final Long startDate,
            @RequestParam(value = "end_date", required = false) final Long endDate) throws URISyntaxException,
        IOException {

        if ( !kairosDBConfig.isEnabled() ) {
            return new ResponseEntity<>(new CheckHistoryResult(),HttpStatus.METHOD_NOT_ALLOWED);
        }

        final QueryBuilder builder = QueryBuilder.getInstance();

        if (hours != 0) {
            builder.setStart(hours, TimeUnit.HOURS);
        } else if (days != 0) {
            builder.setStart(days, TimeUnit.DAYS);
        } else if (null != startDate) {
            builder.setStart(new Date(startDate));
        } else {
            builder.setStart(1, TimeUnit.DAYS);
        }

        if (hoursEnd != 0) {
            builder.setEnd(hoursEnd, TimeUnit.HOURS);
        } else if (daysEnd != 0) {
            builder.setEnd(daysEnd, TimeUnit.DAYS);
        } else if (null != endDate) {
            builder.setEnd(new Date(endDate));
        }

        final QueryMetric metric = builder.addMetric("zmon.check." + checkId)
                                          .addTag("entity", entityId.replace(":", "_").replace("[","_").replace("]","_").replace("@", "_")).addGrouper(
                                              new TagGrouper("key"));

        if ("hours".equals(aggregateUnit)) {
            metric.addAggregator(AggregatorFactory.createAverageAggregator(aggregate, TimeUnit.HOURS));
        } else if ("days".equals(aggregateUnit)) {
            metric.addAggregator(AggregatorFactory.createAverageAggregator(aggregate, TimeUnit.DAYS));
        } else {
            metric.addAggregator(AggregatorFactory.createAverageAggregator(aggregate, TimeUnit.MINUTES));
        }

        final HttpClient client = new HttpClient("http://" + kairosDBConfig.getHost() + ":" + kairosDBConfig.getPort());
        try {
            final Long queryStart = System.currentTimeMillis();
            final QueryResponse response = client.query(builder);
            final Long queryEnd = System.currentTimeMillis();

            LOG.info("Querying kairosdb for check/entity {}/{} in {}ms aggregate: {} {} range: {} - {}", checkId,
                entityId, queryEnd - queryStart, aggregate, aggregateUnit,
                builder.getStartAbsolute() != null ? builder.getStartAbsolute() : builder.getStartRelative(),
                builder.getEndAbsolute() != null ? builder.getEndAbsolute() : builder.getEndRelative());

            final CheckHistoryResult r = new CheckHistoryResult();
            r.entityId = entityId;
            r.name = "zmon.check." + checkId;

            for (final Queries qs : response.getQueries()) {
                for (final Results rs : qs.getResults()) {
                    final CheckHistoryGroupResult groupResult = new CheckHistoryGroupResult();

                    if (null == rs.getGroupResults()) {

                        // no values returned from kairosdb for this entity/check
                        continue;
                    }

                    final TagGroupResult tagGroup = (TagGroupResult) rs.getGroupResults().get(0);
                    groupResult.key = tagGroup.getGroup().get("key");

                    r.groupResults.add(groupResult);

                    for (final DataPoint dp : rs.getDataPoints()) {
                        final List<JsonNode> l = new ArrayList<>();

                        try {
                            if (dp.isIntegerValue()) {
                                l.add(new LongNode(dp.getTimestamp()));
                                l.add(new LongNode(dp.longValue()));
                            } else {
                                l.add(new LongNode(dp.getTimestamp()));
                                l.add(new DoubleNode(dp.doubleValue()));
                            }
                        } catch (DataFormatException dfe) {
                            
                        }

                        groupResult.values.add(l);
                    }
                }
            }

            return new ResponseEntity<>(r, HttpStatus.OK);
        } finally {
            client.shutdown();
        }
    }

    @Autowired
    GrafanaDashboardSprocService grafanaService;

    @Autowired
    DefaultZMonPermissionService authService;

    @Autowired
    ObjectMapper mapper;

    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    @RequestMapping(value = "/grafana/dashboard/{id}", method = RequestMethod.PUT)
    public void putDashboard(@PathVariable(value="id") String id, @RequestBody JsonNode grafanaData) throws ZMonException, JsonProcessingException {
        String title = grafanaData.get("title").asText();
        String dashboard = grafanaData.get("dashboard").asText();

        LOG.info("dashboard: {} {}", title, dashboard);
        grafanaService.createOrUpdateGrafanaDashboard(id, title, dashboard, authService.getUserName());
    }

    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    @RequestMapping(value = "/grafana/dashboard/{id}", method = RequestMethod.GET)
    public JsonNode getDashboard(@PathVariable(value="id") String id) throws ZMonException {
        List<GrafanaDashboardSprocService.GrafanaDashboard> dashboards = grafanaService.getGrafanaDashboard(id);
        if(dashboards.isEmpty()) {
            LOG.info("no dashboard found for id {}", id);
            return null;
        }

        ObjectNode node = mapper.createObjectNode();
        ObjectNode sourceNode = mapper.createObjectNode();

        node.put("found", true);
        node.put("_type", "dashboard");
        node.put("_id", id);
        node.put("_source", sourceNode);
        sourceNode.put("title", dashboards.get(0).title);
        sourceNode.put("tags", mapper.createArrayNode());
        sourceNode.put("dashboard", dashboards.get(0).dashboard);

        return node;
    }

    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    @RequestMapping(value = "/grafana/dashboard/_search", method = RequestMethod.POST)
    public JsonNode getDashboards(@RequestBody JsonNode grafanaSearch) throws ZMonException {
        ObjectNode r = mapper.createObjectNode();
        ObjectNode hits = mapper.createObjectNode();
        ObjectNode facets = mapper.createObjectNode();
        ObjectNode facetsTags = mapper.createObjectNode();
        ArrayNode facetsTagsTerms = mapper.createArrayNode();
        ArrayNode hitsHits = mapper.createArrayNode();
        List<GrafanaDashboardSprocService.GrafanaDashboard> dashboards = grafanaService.getGrafanaDashboards();

        for(GrafanaDashboardSprocService.GrafanaDashboard d : dashboards) {

            ObjectNode hit = mapper.createObjectNode();
            ObjectNode source = mapper.createObjectNode();

            hit.put("_id", d.id);
            hit.put("_type", "dashboard");
            hit.put("_source", source);

            source.put("dashboard","");
            source.put("tags", mapper.createArrayNode());
            source.put("title", d.title);
            source.put("user", d.user);
            source.put("group", d.user);

            hitsHits.add(hit);

        }

        facetsTags.put("_type", "terms");
        facetsTags.put("missing", 0);
        facetsTags.put("other", 0);
        facetsTags.put("total", hitsHits.size());
        facetsTags.put("terms", facetsTagsTerms);
        facets.put("tags", facetsTags);
        hits.put("total", dashboards.size());
        hits.put("hits", hitsHits);
        r.put("hits", hits);
        r.put("facets", facets);
        r.put("timed_out", false);

        return r;
    }

    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    @RequestMapping(value = "lastResults/{checkId}/{filter}", method = RequestMethod.GET)
    public ResponseEntity<CheckChartResult> getLastResults(@PathVariable(value="checkId") String checkId, @PathVariable(value="filter") String filter, @RequestParam(value="limit", defaultValue="1") int limit) throws ZMonException {
        CheckChartResult cr = service.getFilteredLastResults(checkId, filter, limit);
        return new ResponseEntity<>(cr, HttpStatus.OK);
    }
}
