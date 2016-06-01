package org.unidal.cat.plugin.transaction.page;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.servlet.ServletException;

import org.unidal.cat.plugin.transaction.TransactionConstants;
import org.unidal.cat.plugin.transaction.filter.*;
import org.unidal.cat.plugin.transaction.page.DisplayNames.TransactionNameModel;
import org.unidal.cat.plugin.transaction.page.GraphPayload.AverageTimePayload;
import org.unidal.cat.plugin.transaction.page.GraphPayload.DurationPayload;
import org.unidal.cat.plugin.transaction.page.GraphPayload.FailurePayload;
import org.unidal.cat.plugin.transaction.page.GraphPayload.HitPayload;
import org.unidal.cat.plugin.transaction.page.transform.AllReportDistributionBuilder;
import org.unidal.cat.spi.ReportManager;
import org.unidal.cat.spi.ReportPeriod;
import org.unidal.lookup.annotation.Inject;
import org.unidal.lookup.util.StringUtils;
import org.unidal.web.mvc.PageHandler;
import org.unidal.web.mvc.annotation.InboundActionMeta;
import org.unidal.web.mvc.annotation.OutboundActionMeta;
import org.unidal.web.mvc.annotation.PayloadMeta;

import com.dianping.cat.Constants;
import com.dianping.cat.consumer.transaction.model.entity.TransactionName;
import com.dianping.cat.consumer.transaction.model.entity.TransactionReport;
import com.dianping.cat.consumer.transaction.model.entity.TransactionType;
import com.dianping.cat.helper.JsonBuilder;
import com.dianping.cat.mvc.PayloadNormalizer;
import com.dianping.cat.report.ReportPage;
import com.dianping.cat.report.graph.PieChart;
import com.dianping.cat.report.graph.PieChart.Item;
import com.dianping.cat.report.graph.svg.GraphBuilder;
import com.dianping.cat.report.page.transaction.transform.DistributionDetailVisitor;
import com.dianping.cat.report.page.transaction.transform.PieGraphChartVisitor;

public class Handler implements PageHandler<Context> {
	@Inject
	private GraphBuilder m_builder;

	@Inject
	private HistoryGraphs m_historyGraph;

	@Inject
	private JspViewer m_jspViewer;

	@Inject
	private PayloadNormalizer m_normalizer;

	@Inject
	private AllReportDistributionBuilder m_distributionBuilder;

	@Inject(TransactionConstants.NAME)
	private ReportManager<TransactionReport> m_manager;

	private void buildDistributionInfo(Model model, String type, String name, TransactionReport report) {
		PieGraphChartVisitor chartVisitor = new PieGraphChartVisitor(type, name);
		DistributionDetailVisitor detailVisitor = new DistributionDetailVisitor(type, name);

		chartVisitor.visitTransactionReport(report);
		detailVisitor.visitTransactionReport(report);
		model.setDistributionChart(chartVisitor.getPieChart().getJsonString());
		model.setDistributionDetails(detailVisitor.getDetails());
	}

	private void buildAllReportDistributionInfo(Model model, String type, String name, String ip, TransactionReport report) {
		m_distributionBuilder.buildAllReportDistributionInfo(model, type, name, ip, report);
	}

	private void buildTransactionMetaInfo(Model model, Payload payload, TransactionReport report) {
		String type = payload.getType();
		String sorted = payload.getSortBy();
		String queryName = payload.getQueryName();
		String ip = payload.getIpAddress();

		if (!StringUtils.isEmpty(type)) {
			DisplayNames displayNames = new DisplayNames();

			model.setDisplayNameReport(displayNames.display(sorted, type, ip, report, queryName));
			buildTransactionNamePieChart(displayNames.getResults(), model);
		} else {
			model.setDisplayTypeReport(new DisplayTypes().display(sorted, ip, report));
		}
	}

	private void buildTransactionNameGraph(Model model, TransactionReport report, String type, String name, String ip) {
		if (name == null || name.length() == 0) {
			name = Constants.ALL;
		}

		TransactionType t = report.findOrCreateMachine(ip).findOrCreateType(type);
		TransactionName transactionName = t.findOrCreateName(name);

		if (transactionName != null) {
			String graph1 = m_builder.build(new DurationPayload("Duration Distribution", "Duration (ms)", "Count",
			      transactionName));
			String graph2 = m_builder.build(new HitPayload("Hits Over Time", "Time (min)", "Count", transactionName));
			String graph3 = m_builder.build(new AverageTimePayload("Average Duration Over Time", "Time (min)",
			      "Average Duration (ms)", transactionName));
			String graph4 = m_builder.build(new FailurePayload("Failures Over Time", "Time (min)", "Count",
			      transactionName));

			model.setGraph1(graph1);
			model.setGraph2(graph2);
			model.setGraph3(graph3);
			model.setGraph4(graph4);
		}
	}

	private void buildTransactionNamePieChart(List<TransactionNameModel> names, Model model) {
		PieChart chart = new PieChart();
		List<Item> items = new ArrayList<Item>();

		for (int i = 1; i < names.size(); i++) {
			TransactionNameModel name = names.get(i);
			Item item = new Item();
			TransactionName transaction = name.getDetail();
			item.setNumber(transaction.getTotalCount()).setTitle(transaction.getId());
			items.add(item);
		}

		chart.addItems(items);
		model.setPieChart(new JsonBuilder().toJson(chart));
	}

	private void handleHistoryGraph(Model model, Payload payload) throws IOException {
		String filterId;
		if(payload.getDomain().equals(Constants.ALL)){
			filterId = payload.getName() == null ? TransactionAllTypeGraphFilter.ID : TransactionAllNameGraphFilter.ID;
		} else {
			filterId = payload.getName() == null ? TransactionTypeGraphFilter.ID : TransactionNameGraphFilter.ID;
		}

		ReportPeriod period = payload.getReportPeriod();
		String domain = payload.getDomain();
		Date date = payload.getStartTime();
		TransactionReport current = m_manager.getReport(period, period.getStartTime(date), domain, filterId, //
		      "ip", payload.getIpAddress(), //
		      "type", payload.getType(), //
		      "name", payload.getName());
		TransactionReport last = m_manager.getReport(period, period.getLastStartTime(date), domain, filterId, //
		      "ip", payload.getIpAddress(), //
		      "type", payload.getType(), //
		      "name", payload.getName());
		TransactionReport baseline = m_manager.getReport(period, period.getBaselineStartTime(date), domain, filterId, //
		      "ip", payload.getIpAddress(), //
		      "type", payload.getType(), //
		      "name", payload.getName());

		model.setReport(current);

		if (current != null) {
			String type = payload.getType();
			String name = payload.getName();
			String ip = payload.getIpAddress();

			if (Constants.ALL.equalsIgnoreCase(ip)) {
				buildDistributionInfo(model, type, name, current);
			} else if (Constants.ALL.equals(payload.getDomain())) {
				buildAllReportDistributionInfo(model, type, name, ip, current);
			}
		}

		m_historyGraph.buildTrend(model, current, last, baseline);
		// m_historyGraph.buildTrendGraph(model, payload);
	}

	private void handleHistoryReport(Model model, Payload payload) throws IOException {
		String filterId;
		if(payload.getDomain().equals(Constants.ALL)){
			filterId = payload.getType() == null ? TransactionAllTypeFilter.ID : TransactionAllNameFilter.ID;
		} else {
			filterId = payload.getType() == null ? TransactionTypeFilter.ID : TransactionNameFilter.ID;
		}

		ReportPeriod period = payload.getReportPeriod();
		Date startTime = payload.getStartTime();
		TransactionReport report = m_manager.getReport(period, startTime, payload.getDomain(), filterId, //
		      "ip", payload.getIpAddress(), //
		      "type", payload.getType());

		if (report != null) {
			buildTransactionMetaInfo(model, payload, report);
		}

		model.setReport(report);
	}

	private void handleHourlyGraph(Model model, Payload payload) throws IOException {
		String filterId;
		if(payload.getDomain().equals(Constants.ALL)){
			filterId = payload.getName() == null ? TransactionAllTypeGraphFilter.ID : TransactionAllNameGraphFilter.ID;
		} else {
			filterId = payload.getName() == null ? TransactionTypeGraphFilter.ID : TransactionNameGraphFilter.ID;
		}

		Date startTime = payload.getStartTime();
		TransactionReport report = m_manager.getReport(ReportPeriod.HOUR, startTime, payload.getDomain(), filterId, //
		      "ip", payload.getIpAddress(), //
		      "type", payload.getType(), //
		      "name", payload.getName());

		if (report != null) {
			String type = payload.getType();
			String name = payload.getName();
			String ip = payload.getIpAddress();

			if (Constants.ALL.equalsIgnoreCase(ip)) {
				buildDistributionInfo(model, type, name, report);
			} else if (Constants.ALL.equals(payload.getDomain())) {
				buildAllReportDistributionInfo(model, type, name, ip, report);
			}

			buildTransactionNameGraph(model, report, type, name, ip);
		}

		model.setReport(report);
	}

	private void handleHourlyReport(Model model, Payload payload) throws IOException {
		String filterId;
		if(payload.getDomain().equals(Constants.ALL)){
			filterId = payload.getType() == null ? TransactionAllTypeFilter.ID : TransactionAllNameFilter.ID;
		} else {
			filterId = payload.getType() == null ? TransactionTypeFilter.ID : TransactionNameFilter.ID;
		}

		Date startTime = payload.getStartTime();
		TransactionReport report = m_manager.getReport(ReportPeriod.HOUR, startTime, payload.getDomain(), filterId, //
		      "ip", payload.getIpAddress(), //
		      "type", payload.getType());

		if (report != null) {
			buildTransactionMetaInfo(model, payload, report);
		} else {
			report = new TransactionReport(payload.getDomain());
			report.setPeriod(ReportPeriod.HOUR);
			report.setStartTime(startTime);
		}

		model.setReport(report);
	}

	@Override
	@PayloadMeta(Payload.class)
	@InboundActionMeta(name = "t")
	public void handleInbound(Context ctx) throws ServletException, IOException {
		// display only, no action here
	}

	@Override
	@OutboundActionMeta(name = "t")
	public void handleOutbound(Context ctx) throws ServletException, IOException {
		Model model = new Model(ctx);
		Payload payload = ctx.getPayload();
		Action action = payload.getAction();

		normalizePayload(model, payload);

		switch (action) {
		case HOURLY_REPORT:
			handleHourlyReport(model, payload);
			break;
		case HOURLY_GRAPH:
			handleHourlyGraph(model, payload);
			break;
		case HISTORY_REPORT:
			handleHistoryReport(model, payload);
			break;
		case HISTORY_GRAPH:
			handleHistoryGraph(model, payload);
			break;
		}

		TransactionReport report = model.getReport();
		Date startTime = report.getStartTime();

        // TODO for history report, endTime should not be startTime + HOUR
		Date endTime = ReportPeriod.HOUR.getNextStartTime(startTime);

		report.setEndTime(new Date(endTime.getTime() - 1000));

		if (!ctx.isProcessStopped()) {
			m_jspViewer.view(ctx, model);
		}
	}

	private void normalizePayload(Model model, Payload payload) {
		m_normalizer.normalize(model, payload);

		model.setPage(ReportPage.TRANSACTION);
		model.setAction(payload.getAction());
		model.setQueryName(payload.getQueryName());
	}
}
