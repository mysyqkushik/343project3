package com.groupxx.greengrocer.controller;

import com.groupxx.greengrocer.dao.OrderDao;
import com.groupxx.greengrocer.dao.UserDao;
import com.groupxx.greengrocer.model.CarrierSummaryRecord;
import com.groupxx.greengrocer.model.DailySalesRecord;
import com.groupxx.greengrocer.util.Alerts;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ComboBox;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Owner reports: basic charts (daily sales + carrier ratings). */
public final class OwnerReportsController {

    @FXML private ComboBox<Integer> daysCombo;

    @FXML private LineChart<String, Number> salesChart;
    @FXML private BarChart<String, Number> ratingsChart;

    private final OrderDao orderDao = new OrderDao();
    private final UserDao userDao = new UserDao();

    @FXML
    public void initialize() {
        daysCombo.setItems(FXCollections.observableArrayList(7, 14, 30, 60, 90));
        daysCombo.getSelectionModel().select(Integer.valueOf(30));
        daysCombo.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> reload());

        // Make charts look clean
        salesChart.setAnimated(false);
        ratingsChart.setAnimated(false);

        reload();
    }

    /** Called by OwnerHomeController "Refresh" button. */
    public void reload() {
        try {
            int days = daysCombo.getSelectionModel().getSelectedItem() == null ? 30 : daysCombo.getSelectionModel().getSelectedItem();
            reloadSales(days);
            reloadRatings();
        } catch (Exception ex) {
            Alerts.showError("Reports Failed", "Cannot load reports.", ex.getMessage());
        }
    }

    @FXML
    public void onReload() {
        reload();
    }

    private void reloadSales(int days) throws Exception {
        List<DailySalesRecord> rows = orderDao.listDailySalesLastDays(days);
        Map<LocalDate, DailySalesRecord> byDay = new HashMap<>();
        for (DailySalesRecord r : rows) {
            byDay.put(r.day(), r);
        }

        XYChart.Series<String, Number> ordersSeries = new XYChart.Series<>();
        ordersSeries.setName("Orders/day");

        XYChart.Series<String, Number> revenueSeries = new XYChart.Series<>();
        revenueSeries.setName("Revenue/day (incl. VAT)");

        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(days - 1L);
        for (LocalDate d = start; !d.isAfter(today); d = d.plusDays(1)) {
            DailySalesRecord r = byDay.get(d);
            long orders = (r == null) ? 0 : r.orders();
            BigDecimal revenue = (r == null || r.revenue() == null) ? BigDecimal.ZERO : r.revenue();

            String label = d.toString();
            ordersSeries.getData().add(new XYChart.Data<>(label, orders));
            revenueSeries.getData().add(new XYChart.Data<>(label, revenue.doubleValue()));
        }

        salesChart.getData().setAll(ordersSeries, revenueSeries);
    }

    private void reloadRatings() throws Exception {
        List<CarrierSummaryRecord> carriers = userDao.listCarriersWithAvgRating();
        XYChart.Series<String, Number> s = new XYChart.Series<>();
        s.setName("Average carrier rating");

        for (CarrierSummaryRecord c : carriers) {
            double v = (c.avgRating() == null) ? 0.0 : c.avgRating();
            s.getData().add(new XYChart.Data<>(c.username(), v));
        }

        ratingsChart.getData().setAll(s);
    }
}
