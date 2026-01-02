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
import javafx.scene.control.TextField;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Controller for the Owner Reports Screen.
 * <p>
 * Visualization of sales, orders, and customer statistics using charts.
 * Allows filtering by date range (7, 30, 90 days) and visualizing carrier
 * performance.
 * </p>
 */
public final class OwnerReportsController {

    @FXML
    private ComboBox<Integer> daysCombo;

    @FXML
    private LineChart<String, Number> customersChart;
    @FXML
    private LineChart<String, Number> salesChart;
    @FXML
    private BarChart<String, Number> ratingsChart;

    @FXML
    private TextField carrierSearchField;

    private final OrderDao orderDao = new OrderDao();
    private final UserDao userDao = new UserDao();

    private List<CarrierSummaryRecord> allCarriers = new ArrayList<>();

    /**
     * Initializes the controller.
     * Sets up the days combo box and chart animations.
     */
    @FXML
    public void initialize() {
        daysCombo.setItems(FXCollections.observableArrayList(7, 14, 30, 60, 90));
        daysCombo.getSelectionModel().select(Integer.valueOf(30));
        daysCombo.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> reload());

        // Carrier filter listeners
        carrierSearchField.textProperty().addListener((o, ov, nv) -> applyCarrierFilter());

        // Make charts look clean
        if (customersChart != null)
            customersChart.setAnimated(false);
        salesChart.setAnimated(false);
        ratingsChart.setAnimated(false);

        reload();
    }

    /**
     * Reloads all reports based on the selected date range.
     * Called by the "Refresh" button in the parent Owner Dashboard.
     */
    public void reload() {
        try {
            int days = daysCombo.getSelectionModel().getSelectedItem() == null ? 30
                    : daysCombo.getSelectionModel().getSelectedItem();
            reloadSales(days);
            reloadCustomerStats(days);
            reloadCarriers();
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

    private void reloadCustomerStats(int days) throws Exception {
        if (customersChart == null)
            return;

        List<LocalDate> creationDates = userDao.getCustomerCreationDates();
        Map<LocalDate, Set<Long>> dailyActive = orderDao.getDailyCustomersLastDays(days);

        XYChart.Series<String, Number> totalSeries = new XYChart.Series<>();
        totalSeries.setName("Total Customers");

        XYChart.Series<String, Number> activeSeries = new XYChart.Series<>();
        activeSeries.setName("Active (last 5 days)");

        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(days - 1L);

        // Sort creation dates
        List<LocalDate> sortedCreation = creationDates.stream().sorted().collect(Collectors.toList());

        for (LocalDate d = start; !d.isAfter(today); d = d.plusDays(1)) {
            final LocalDate currentDay = d;

            // Total customers created on or before currentDay
            long total = sortedCreation.stream().filter(cd -> !cd.isAfter(currentDay)).count();

            // Active in [d-4, d]
            Set<Long> uniqueActive = new HashSet<>();
            for (int i = 0; i < 5; i++) {
                LocalDate lookback = d.minusDays(i);
                Set<Long> s = dailyActive.get(lookback);
                if (s != null)
                    uniqueActive.addAll(s);
            }

            String label = d.toString();
            totalSeries.getData().add(new XYChart.Data<>(label, total));
            activeSeries.getData().add(new XYChart.Data<>(label, uniqueActive.size()));
        }

        customersChart.getData().setAll(totalSeries, activeSeries);
    }

    private void reloadCarriers() {
        try {
            // "Remove the tick" implies logic simplification. Let's list ALL carriers.
            // userDao.listCarriersWithAvgRating() typically returns only active or
            // something?
            // User requested "Show all (incl. inactive)" logic previously, now removed.
            // I'll stick to listAllCarriersWithAvgRating() to cover everything by default.
            allCarriers = userDao.listAllCarriersWithAvgRating();
            applyCarrierFilter();
        } catch (Exception ex) {
            Alerts.showError("Carriers Failed", "Cannot load carriers.", ex.getMessage());
        }
    }

    private void applyCarrierFilter() {
        String query = carrierSearchField.getText();
        if (query == null)
            query = "";
        query = query.trim().toLowerCase();

        XYChart.Series<String, Number> s = new XYChart.Series<>();
        s.setName("Average carrier rating");

        for (CarrierSummaryRecord c : allCarriers) {
            if (!query.isEmpty() && !c.username().toLowerCase().contains(query)) {
                continue;
            }
            double v = (c.avgRating() == null) ? 0.0 : c.avgRating();
            s.getData().add(new XYChart.Data<>(c.username(), v));
        }

        ratingsChart.getData().setAll(s);
    }
}
