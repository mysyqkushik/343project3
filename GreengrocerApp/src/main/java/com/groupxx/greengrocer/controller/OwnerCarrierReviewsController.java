package com.groupxx.greengrocer.controller;

import com.groupxx.greengrocer.dao.OrderDao;
import com.groupxx.greengrocer.model.CarrierReviewRecord;
import com.groupxx.greengrocer.util.Alerts;
import com.groupxx.greengrocer.util.Formatters;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller for the Owner's Carrier Reviews Tab.
 * <p>
 * Displays a list of all carrier reviews submitted by customers, including
 * ratings,
 * comments, and associated order details.
 * </p>
 */
public final class OwnerCarrierReviewsController {
    private static final Logger LOG = Logger.getLogger(OwnerCarrierReviewsController.class.getName());

    @FXML
    private TableView<CarrierReviewRecord> reviewsTable;
    @FXML
    private TableColumn<CarrierReviewRecord, String> colOrderId;
    @FXML
    private TableColumn<CarrierReviewRecord, String> colCarrier;
    @FXML
    private TableColumn<CarrierReviewRecord, String> colCustomer;
    @FXML
    private TableColumn<CarrierReviewRecord, String> colRating;
    @FXML
    private TableColumn<CarrierReviewRecord, String> colComment;
    @FXML
    private TableColumn<CarrierReviewRecord, String> colDate;

    private final OrderDao orderDao = new OrderDao();

    /**
     * Initializes the controller.
     * Sets up table columns and specific cell factories for comments.
     */
    @FXML
    private void initialize() {
        colOrderId.setCellValueFactory(cd -> new SimpleStringProperty(String.valueOf(cd.getValue().orderId())));
        colCarrier.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().carrierUsername()));
        colCustomer.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().customerUsername()));
        colRating.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().rating() + " / 5"));
        colComment.setCellValueFactory(cd -> {
            String comment = cd.getValue().comment();
            return new SimpleStringProperty(comment == null || comment.isBlank() ? "-" : comment);
        });
        colDate.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue().reviewTime() == null ? "-" : Formatters.formatDateTime(cd.getValue().reviewTime())));

        // Enable text wrapping for comment column
        colComment.setCellFactory(tc -> {
            TableCell<CarrierReviewRecord, String> cell = new TableCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setTooltip(null);
                    } else {
                        setText(item);
                        // Show full comment in tooltip if it's long
                        if (item.length() > 50) {
                            setTooltip(new Tooltip(item));
                        }
                    }
                }
            };
            cell.setWrapText(true);
            return cell;
        });

        load();
    }

    @FXML
    private void onRefresh() {
        load();
    }

    /**
     * Loads the carrier reviews from the database asynchronously.
     */
    public void load() {
        new Thread(() -> {
            try {
                List<CarrierReviewRecord> reviews = orderDao.listCarrierReviews();
                Platform.runLater(() -> reviewsTable.getItems().setAll(reviews));
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "Failed to load carrier reviews", e);
                Platform.runLater(
                        () -> Alerts.showError("Load Failed", "Cannot load carrier reviews.", e.getMessage()));
            }
        }, "load-carrier-reviews").start();
    }
}
