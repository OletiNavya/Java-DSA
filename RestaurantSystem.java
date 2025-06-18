package application;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.collections.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import java.sql.*;
import java.text.DecimalFormat;
import java.time.LocalDateTime; // Not directly used in latest logic, but good to have if needed for timestamp formatting

// For JavaFX properties
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class Main extends Application {

    private Stage primaryStage;
    private Connection conn;

    // --- Data Models (Inner Classes) ---

    // MenuItem: Represents an item from the menu
    public static class MenuItem {
        private final IntegerProperty id;
        private final StringProperty name;
        private final DoubleProperty price;

        public MenuItem(int id, String name, double price) {
            this.id = new SimpleIntegerProperty(id);
            this.name = new SimpleStringProperty(name);
            this.price = new SimpleDoubleProperty(price);
        }

        public IntegerProperty idProperty() { return id; }
        public StringProperty nameProperty() { return name; }
        public DoubleProperty priceProperty() { return price; }

        public int getId() { return id.get(); }
        public String getName() { return name.get(); }
        public double getPrice() { return price.get(); }

        public void setId(int id) { this.id.set(id); }
        public void setName(String name) { this.name.set(name); }
        public void setPrice(double price) { this.price.set(price); }
    }

    public static class CartItem extends MenuItem {
        private final IntegerProperty quantity;
        private final DoubleProperty subtotal; // Price * Quantity

        public CartItem(MenuItem item, int quantity) {
            super(item.getId(), item.getName(), item.getPrice());
            this.quantity = new SimpleIntegerProperty(quantity);
            this.subtotal = new SimpleDoubleProperty(item.getPrice() * quantity);
            // Listener to update subtotal if quantity or price changes
            this.quantity.addListener((obs, oldVal, newVal) -> updateSubtotal());
            this.priceProperty().addListener((obs, oldVal, newVal) -> updateSubtotal());
        }

        public IntegerProperty quantityProperty() { return quantity; }
        public DoubleProperty subtotalProperty() { return subtotal; }

        public int getQuantity() { return quantity.get(); }
        public double getSubtotal() { return subtotal.get(); }

        public void setQuantity(int quantity) { this.quantity.set(quantity); }

        private void updateSubtotal() {
            this.subtotal.set(getPrice() * getQuantity());
        }
    }

    public static class OrderHistoryItem {
        private final IntegerProperty orderId;
        private final IntegerProperty billId;
        private final StringProperty itemName;
        private final DoubleProperty itemPrice;
        private final IntegerProperty quantity;
        private final DoubleProperty subtotal;
        private final StringProperty billTime; // Display as String for simplicity

        public OrderHistoryItem(int orderId, int billId, String itemName, double itemPrice, int quantity, double subtotal, Timestamp billTime) {
            this.orderId = new SimpleIntegerProperty(orderId);
            this.billId = new SimpleIntegerProperty(billId);
            this.itemName = new SimpleStringProperty(itemName);
            this.itemPrice = new SimpleDoubleProperty(itemPrice);
            this.quantity = new SimpleIntegerProperty(quantity);
            this.subtotal = new SimpleDoubleProperty(subtotal);
            this.billTime = new SimpleStringProperty(billTime != null ? billTime.toString() : "N/A");
        }

        public IntegerProperty orderIdProperty() { return orderId; }
        public IntegerProperty billIdProperty() { return billId; }
        public StringProperty itemNameProperty() { return itemName; }
        public DoubleProperty itemPriceProperty() { return itemPrice; }
        public IntegerProperty quantityProperty() { return quantity; }
        public DoubleProperty subtotalProperty() { return subtotal; }
        public StringProperty billTimeProperty() { return billTime; }

        public int getOrderId() { return orderId.get(); }
        public int getBillId() { return billId.get(); }
        public String getItemName() { return itemName.get(); }
        public double getItemPrice() { return itemPrice.get(); }
        public int getQuantity() { return quantity.get(); }
        public double getSubtotal() { return subtotal.get(); }
        public String getBillTime() { return billTime.get(); }
    }


    // --- UI Components for Menu/Ordering ---
    private TableView<MenuItem> menuTable = new TableView<>();
    private ObservableList<MenuItem> menuData = FXCollections.observableArrayList();
    private ObservableList<CartItem> cartData = FXCollections.observableArrayList();
    private Label totalBillLabel = new Label("Total: 0.00");
    private DecimalFormat df = new DecimalFormat("0.00"); // For formatting currency

    // --- UI Components for Admin Portal ---
    private TableView<MenuItem> adminMenuTable = new TableView<>();
    private ObservableList<MenuItem> adminMenuData = FXCollections.observableArrayList();
    private TextField adminItemIdField = new TextField(); // For adding/updating menu item ID
    private TextField adminItemNameField = new TextField();
    private TextField adminItemPriceField = new TextField();

    private TableView<OrderHistoryItem> orderHistoryTable = new TableView<>();
    private ObservableList<OrderHistoryItem> orderHistoryData = FXCollections.observableArrayList();


    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        primaryStage.setTitle("Restaurant Application");

        // --- Driver Loading ---
        try {
            Class.forName("oracle.jdbc.driver.OracleDriver"); //
        } catch (ClassNotFoundException e) {
            showAlert("Driver Error", "Oracle JDBC Driver not found. Make sure ojdbcX.jar is in your classpath.");
            e.printStackTrace();
            return;
        }

        connectDB(); // Establish connection on startup

        // Show login screen first
        showLoginScreen();
    }

    private void connectDB() {
        try {
            // !!! IMPORTANT: Replace 'your_oracle_username' and 'your_oracle_password' with your actual Oracle DB credentials !!!
            // The ORA-01017 error indicates this is incorrect.
            String oracleUrl = "jdbc:oracle:thin:@localhost:1521:XE"; // Adjust port/SID if different for your Oracle XE
            String username = "system"; // <<< REPLACE THIS
            String password = "mydbms123"; // <<< REPLACE THIS

            conn = DriverManager.getConnection(oracleUrl, username, password);
            conn.setAutoCommit(false); // Explicitly set auto-commit to false for Oracle to use manual commit/rollback
            System.out.println("Oracle Database connected successfully! Auto-commit is OFF.");
        } catch (SQLException e) {
            showAlert("Database Connection Error", "Failed to connect to Oracle database: " + e.getMessage() + "\n" +
                      "Please check your username, password, and database URL.");
            e.printStackTrace();
            System.exit(1);
        }
    }

    // --- Bill Generation (Order Placing) Logic ---

    private void showOrderingScreen() {
        // --- Menu Table Setup ---
        TableColumn<MenuItem, Integer> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(cell -> cell.getValue().idProperty().asObject());
        idCol.setPrefWidth(50);

        TableColumn<MenuItem, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(cell -> cell.getValue().nameProperty());
        nameCol.setPrefWidth(200);

        TableColumn<MenuItem, Double> priceCol = new TableColumn<>("Price");
        priceCol.setCellValueFactory(cell -> cell.getValue().priceProperty().asObject());
        priceCol.setPrefWidth(100);

        menuTable.getColumns().addAll(idCol, nameCol, priceCol);
        menuTable.setItems(menuData);
        menuTable.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        loadMenuItems(); // Load initial data for the menu

        // --- Add to Cart Controls ---
        Spinner<Integer> quantitySpinner = new Spinner<>(1, 10, 1); // Min, Max, Initial
        quantitySpinner.setEditable(true); // Allow manual quantity input
        TextFormatter<Integer> textFormatter = new TextFormatter<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 100, 1).getConverter());
        quantitySpinner.getEditor().setTextFormatter(textFormatter);
        //textFormatter.valueProperty().bindBidirectional(quantitySpinner.valueProperty());

        Button addToCartBtn = new Button("Add to Cart");
        addToCartBtn.setOnAction(e -> {
            MenuItem selectedItem = menuTable.getSelectionModel().getSelectedItem();
            if (selectedItem != null) {
                int quantity = quantitySpinner.getValue();
                if (quantity > 0) {
                    addToCart(selectedItem, quantity);
                } else {
                    showAlert("Invalid Quantity", "Please enter a quantity greater than 0.");
                }
            } else {
                showAlert("No Selection", "Please select an item from the menu to add to cart.");
            }
        });

        HBox menuControls = new HBox(10, new Label("Quantity:"), quantitySpinner, addToCartBtn);
        menuControls.setAlignment(Pos.CENTER_LEFT);

        VBox menuSection = new VBox(10, new Label("Available Menu Items:"), menuTable, menuControls);
        menuSection.setPadding(new Insets(10));
        menuSection.setStyle("-fx-border-color: lightgray; -fx-border-width: 1; -fx-padding: 10;");


        // --- Shopping Cart Table Setup ---
        TableView<CartItem> cartTable = new TableView<>();
        TableColumn<CartItem, String> cartNameCol = new TableColumn<>("Item");
        cartNameCol.setCellValueFactory(cell -> cell.getValue().nameProperty());
        cartNameCol.setPrefWidth(150);

        TableColumn<CartItem, Integer> cartQuantityCol = new TableColumn<>("Qty");
        cartQuantityCol.setCellValueFactory(cell -> cell.getValue().quantityProperty().asObject());
        cartQuantityCol.setCellFactory(tc -> new EditingCell()); // Allow editing quantity
        cartQuantityCol.setOnEditCommit(event -> {
            CartItem item = event.getRowValue();
            item.setQuantity(event.getNewValue());
            updateTotalBill();
        });

        TableColumn<CartItem, Double> cartSubtotalCol = new TableColumn<>("Subtotal");
        cartSubtotalCol.setCellValueFactory(cell -> cell.getValue().subtotalProperty().asObject());
        cartSubtotalCol.setPrefWidth(100);

        cartTable.getColumns().addAll(cartNameCol, cartQuantityCol, cartSubtotalCol);
        cartTable.setItems(cartData);
        cartTable.setEditable(true); // Enable editing for quantity

        Button removeItemBtn = new Button("Remove Selected");
        removeItemBtn.setOnAction(e -> {
            CartItem selectedCartItem = cartTable.getSelectionModel().getSelectedItem();
            if (selectedCartItem != null) {
                cartData.remove(selectedCartItem);
                updateTotalBill();
            } else {
                showAlert("No Selection", "Please select an item from the cart to remove.");
            }
        });

        Button finalizeBillBtn = new Button("Finalize Bill");
        finalizeBillBtn.setOnAction(e -> finalizeBill());

        HBox cartControls = new HBox(10, removeItemBtn, finalizeBillBtn);
        cartControls.setAlignment(Pos.CENTER_LEFT);

        VBox cartSection = new VBox(10, new Label("Current Order:"), cartTable, totalBillLabel, cartControls);
        cartSection.setPadding(new Insets(10));
        cartSection.setStyle("-fx-border-color: lightgray; -fx-border-width: 1; -fx-padding: 10;");

        // --- Main Layout ---
        SplitPane mainLayout = new SplitPane();
        mainLayout.getItems().addAll(menuSection, cartSection);
        mainLayout.setDividerPositions(0.5); // Divide space equally

        Button adminLoginBtn = new Button("Admin Login");
        adminLoginBtn.setOnAction(e -> showLoginScreen()); // Go back to login for admin access

        VBox root = new VBox(10, mainLayout, adminLoginBtn);
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.CENTER);

        primaryStage.setScene(new Scene(root, 900, 600));
        primaryStage.setTitle("Restaurant Order Taking");
        primaryStage.show();
    }

    // Custom cell factory for quantity spinner in cart
    class EditingCell extends TableCell<CartItem, Integer> {
        private Spinner<Integer> spinner;

        @Override
        public void startEdit() {
            if (!isEmpty()) {
                super.startEdit();
                if (spinner == null) {
                    spinner = new Spinner<>(1, 100, getItem()); // Min 1, Max 100, Initial is current value
                    spinner.setEditable(true);
                    spinner.valueProperty().addListener((obs, oldValue, newValue) -> {
                        if (newValue != null) {
                            commitEdit(newValue);
                        }
                    });
                }
                setGraphic(spinner);
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                spinner.requestFocus();
            }
        }

        @Override
        public void cancelEdit() {
            super.cancelEdit();
            setText(String.valueOf(getItem()));
            setContentDisplay(ContentDisplay.TEXT_ONLY);
        }

        @Override
        public void updateItem(Integer item, boolean empty) {
            super.updateItem(item, empty);
            if (empty) {
                setText(null);
                setGraphic(null);
            } else {
                if (isEditing()) {
                    if (spinner != null) {
                        spinner.getValueFactory().setValue(item);
                    }
                    setGraphic(spinner);
                    setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                } else {
                    setText(String.valueOf(item));
                    setContentDisplay(ContentDisplay.TEXT_ONLY);
                }
            }
        }
    }


    private void addToCart(MenuItem item, int quantity) {
        // Check if item already in cart to update quantity instead of adding new row
        boolean found = false;
        for (CartItem cartItem : cartData) {
            if (cartItem.getId() == item.getId()) {
                cartItem.setQuantity(cartItem.getQuantity() + quantity);
                found = true;
                break;
            }
        }
        if (!found) {
            cartData.add(new CartItem(item, quantity));
        }
        updateTotalBill();
    }

    private void updateTotalBill() {
        double total = 0.0;
        for (CartItem item : cartData) {
            total += item.getSubtotal();
        }
        totalBillLabel.setText("Total: " + df.format(total));
    }

    private void finalizeBill() {
        if (cartData.isEmpty()) {
            showAlert("No Items", "The cart is empty. Please add items before finalizing a bill.");
            return;
        }

        double totalAmount = 0.0;
        for (CartItem item : cartData) {
            totalAmount += item.getSubtotal();
        }

        // Use transaction for multiple inserts (Bill + Orders)
        try {
            // 1. Insert into BILLS table to get a new bill_id
            // Using getGeneratedKeys for Oracle 12c+ IDENTITY columns to retrieve the auto-generated ID
            String insertBillSql = "INSERT INTO bills (bill_time, total_amount) VALUES (SYSTIMESTAMP, ?)";
            int newBillId = -1;

            try (PreparedStatement psBill = conn.prepareStatement(insertBillSql, new String[]{"bill_id"})) {
                psBill.setDouble(1, totalAmount);
                int rowsAffectedBill = psBill.executeUpdate();
                if (rowsAffectedBill > 0) {
                    try (ResultSet rs = psBill.getGeneratedKeys()) {
                        if (rs.next()) {
                            newBillId = rs.getInt(1);
                        }
                    }
                }
            }

            if (newBillId == -1) {
                 showAlert("Bill Error", "Could not generate bill ID. Check database logs.");
                 conn.rollback();
                 return;
            }

            // 2. Insert each item from the cart into the ORDERS table
            String insertOrderSql = "INSERT INTO orders (bill_id, item_name, item_price, quantity, subtotal) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement psOrder = conn.prepareStatement(insertOrderSql)) {
                for (CartItem item : cartData) {
                    psOrder.setInt(1, newBillId);
                    psOrder.setString(2, item.getName());
                    psOrder.setDouble(3, item.getPrice());
                    psOrder.setInt(4, item.getQuantity());
                    psOrder.setDouble(5, item.getSubtotal());
                    psOrder.addBatch(); // Add to batch for efficiency
                }
                psOrder.executeBatch(); // Execute all inserts at once
            }

            conn.commit(); // Commit the transaction
            showAlert("Bill Finalized", "Bill #" + newBillId + " finalized successfully! Total: " + df.format(totalAmount));
            cartData.clear(); // Clear cart after bill is finalized
            updateTotalBill(); // Reset total
        } catch (SQLException e) {
            try {
                if (conn != null) conn.rollback(); // Rollback if any error occurs
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
            showAlert("Bill Error", "Failed to finalize bill: " + e.getMessage());
            e.printStackTrace();
        }
    }


    // --- Admin Portal Logic ---

    private void showLoginScreen() {
        GridPane loginLayout = new GridPane();
        loginLayout.setPadding(new Insets(20));
        loginLayout.setVgap(10);
        loginLayout.setHgap(10);
        loginLayout.setAlignment(Pos.CENTER);

        Label userLabel = new Label("Username:");
        TextField usernameField = new TextField();
        usernameField.setPromptText("admin");

        Label passLabel = new Label("Password:");
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("admin123");

        Button loginButton = new Button("Login");
        loginButton.setDefaultButton(true);
        loginButton.setOnAction(e -> {
            if (authenticate(usernameField.getText(), passwordField.getText())) {
                showAdminPortal();
            } else {
                showAlert("Login Failed", "Invalid username or password."); //
            }
        });

        Button backToOrderingBtn = new Button("Back to Ordering");
        backToOrderingBtn.setOnAction(e -> showOrderingScreen());

        loginLayout.add(userLabel, 0, 0);
        loginLayout.add(usernameField, 1, 0);
        loginLayout.add(passLabel, 0, 1);
        loginLayout.add(passwordField, 1, 1);
        loginLayout.add(loginButton, 1, 2);
        loginLayout.add(backToOrderingBtn, 0, 2); // Add back button

        primaryStage.setScene(new Scene(loginLayout, 400, 250));
        primaryStage.setTitle("Admin Login");
        primaryStage.show();
    }

    private boolean authenticate(String username, String password) {
        if (conn == null) {
            showAlert("Error", "Database connection is not established.");
            return false;
        }
        // IMPORTANT: The SQL script creates the 'admin' table.
        // If you are still getting ORA-00942 here, the 'admin' table was not created successfully.
        // You MUST run the SQL script mentioned at the top of this file first.
        String sql = "SELECT password FROM admin WHERE username = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String storedPassword = rs.getString("password");
                    // In a real app, hash and salt passwords, then use a secure comparison (e.g., BCrypt)
                    return storedPassword.equals(password);
                }
            }
        } catch (SQLException e) {
            showAlert("Authentication Error", "Database error during login: " + e.getMessage() + "\n" +
                      "Ensure 'admin' table exists and your database user has access.");
            e.printStackTrace();
        }
        return false;
    }

    private void showAdminPortal() {
        TabPane tabPane = new TabPane();

        // --- Tab 1: Menu Management ---
        Tab menuTab = new Tab("Menu Management");
        menuTab.setClosable(false);

        // Menu table for admin
        TableColumn<MenuItem, Integer> adminIdCol = new TableColumn<>("ID");
        adminIdCol.setCellValueFactory(cell -> cell.getValue().idProperty().asObject());
        TableColumn<MenuItem, String> adminNameCol = new TableColumn<>("Name");
        adminNameCol.setCellValueFactory(cell -> cell.getValue().nameProperty());
        TableColumn<MenuItem, Double> adminPriceCol = new TableColumn<>("Price");
        adminPriceCol.setCellValueFactory(cell -> cell.getValue().priceProperty().asObject());
        adminMenuTable.getColumns().addAll(adminIdCol, adminNameCol, adminPriceCol);
        adminMenuTable.setItems(adminMenuData);
        adminMenuTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                adminItemIdField.setText(String.valueOf(newVal.getId()));
                adminItemNameField.setText(newVal.getName());
                adminItemPriceField.setText(String.valueOf(newVal.getPrice()));
            } else {
                adminItemIdField.clear();
                adminItemNameField.clear();
                adminItemPriceField.clear();
            }
        });
        loadMenuItemsForAdmin(); // Load menu items for admin view

        // Controls for Add/Update/Delete
        adminItemIdField.setPromptText("ID (for Update/Delete)");
        adminItemNameField.setPromptText("Item Name");
        adminItemPriceField.setPromptText("Item Price");

        Button addMenuItemBtn = new Button("Add Item");
        addMenuItemBtn.setOnAction(e -> addMenuItem());
        Button updateMenuItemBtn = new Button("Update Item");
        updateMenuItemBtn.setOnAction(e -> updateMenuItem());
        Button deleteMenuItemBtn = new Button("Delete Item");
        deleteMenuItemBtn.setOnAction(e -> deleteMenuItem());
        Button clearFieldsBtn = new Button("Clear Fields");
        clearFieldsBtn.setOnAction(e -> {
            adminItemIdField.clear();
            adminItemNameField.clear();
            adminItemPriceField.clear();
        });


        HBox adminMenuControls = new HBox(10, addMenuItemBtn, updateMenuItemBtn, deleteMenuItemBtn, clearFieldsBtn);
        adminMenuControls.setAlignment(Pos.CENTER_LEFT);

        VBox menuManagementLayout = new VBox(10,
                new Label("Manage Menu Items:"),
                //new HBox(10, new Label("ID:"), adminItemIdField),
                new HBox(10, new Label("Name:"), adminItemNameField),
                new HBox(10, new Label("Price:"), adminItemPriceField),
                adminMenuControls,
                adminMenuTable
        );
        menuManagementLayout.setPadding(new Insets(10));
        menuTab.setContent(menuManagementLayout);


        // --- Tab 2: View Orders ---
        Tab ordersTab = new Tab("View All Orders");
        ordersTab.setClosable(false);

        // Order History Table
        TableColumn<OrderHistoryItem, Integer> ohOrderIdCol = new TableColumn<>("Order ID");
        ohOrderIdCol.setCellValueFactory(cell -> cell.getValue().orderIdProperty().asObject());
        TableColumn<OrderHistoryItem, Integer> ohBillIdCol = new TableColumn<>("Bill ID");
        ohBillIdCol.setCellValueFactory(cell -> cell.getValue().billIdProperty().asObject());
        TableColumn<OrderHistoryItem, String> ohItemNameCol = new TableColumn<>("Item Name");
        ohItemNameCol.setCellValueFactory(cell -> cell.getValue().itemNameProperty());
        TableColumn<OrderHistoryItem, Double> ohItemPriceCol = new TableColumn<>("Item Price");
        ohItemPriceCol.setCellValueFactory(cell -> cell.getValue().itemPriceProperty().asObject());
        TableColumn<OrderHistoryItem, Integer> ohQuantityCol = new TableColumn<>("Quantity");
        ohQuantityCol.setCellValueFactory(cell -> cell.getValue().quantityProperty().asObject());
        TableColumn<OrderHistoryItem, Double> ohSubtotalCol = new TableColumn<>("Subtotal");
        ohSubtotalCol.setCellValueFactory(cell -> cell.getValue().subtotalProperty().asObject());
        TableColumn<OrderHistoryItem, String> ohBillTimeCol = new TableColumn<>("Bill Time"); // Changed from Order Time
        ohBillTimeCol.setCellValueFactory(cell -> cell.getValue().billTimeProperty());

        orderHistoryTable.getColumns().addAll( ohOrderIdCol, ohBillIdCol, ohItemNameCol, ohItemPriceCol, ohQuantityCol, ohSubtotalCol, ohBillTimeCol);
        orderHistoryTable.setItems(orderHistoryData);

        Button refreshOrdersBtn = new Button("Refresh Orders");
        refreshOrdersBtn.setOnAction(e -> loadOrderHistory());

        VBox ordersLayout = new VBox(10, new Label("All Placed Orders:"), refreshOrdersBtn, orderHistoryTable);
        ordersLayout.setPadding(new Insets(10));
        ordersTab.setContent(ordersLayout);

        // Load order history when the tab is selected
        ordersTab.setOnSelectionChanged(e -> {
            if (ordersTab.isSelected()) {
                loadOrderHistory();
            }
        });


        // --- Logout Button ---
        Button logoutBtn = new Button("Logout");
        logoutBtn.setOnAction(e -> showLoginScreen());

        tabPane.getTabs().addAll(menuTab, ordersTab);

        VBox adminRoot = new VBox(10, tabPane, logoutBtn);
        adminRoot.setPadding(new Insets(20));
        adminRoot.setAlignment(Pos.CENTER);

        primaryStage.setScene(new Scene(adminRoot, 900, 600));
        primaryStage.setTitle("Admin Portal");
        primaryStage.show();
    }

    private void loadMenuItems() {
        menuData.clear();
        if (conn == null) {
            showAlert("Error", "Database connection is not established.");
            return;
        }
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id, name, price FROM menu ORDER BY id")) {
            while (rs.next()) {
                menuData.add(new MenuItem(rs.getInt("id"), rs.getString("name"), rs.getDouble("price")));
            }
            System.out.println("Menu items loaded for order screen: " + menuData.size());
        } catch (SQLException e) {
            showAlert("Load Menu Error", "Failed to load menu items: " + e.getMessage() + "\n" +
                      "Ensure 'menu' table exists and your database user has access.");
            e.printStackTrace();
        }
    }

    private void loadMenuItemsForAdmin() {
        adminMenuData.clear();
        if (conn == null) {
            showAlert("Error", "Database connection is not established.");
            return;
        }
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id, name, price FROM menu ORDER BY id")) {
            while (rs.next()) {
                adminMenuData.add(new MenuItem(rs.getInt("id"), rs.getString("name"), rs.getDouble("price")));
            }
            System.out.println("Menu items loaded for admin: " + adminMenuData.size());
        } catch (SQLException e) {
            showAlert("Load Menu Error", "Failed to load menu items for admin: " + e.getMessage() + "\n" +
                      "Ensure 'menu' table exists and your database user has access.");
            e.printStackTrace();
        }
    }

    private void addMenuItem() {
        if (conn == null) return;
        String name = adminItemNameField.getText();
        double price;
        try {
            price = Double.parseDouble(adminItemPriceField.getText());
        } catch (NumberFormatException e) {
            showAlert("Input Error", "Please enter a valid price (e.g., 100.00).");
            return;
        }
        if (name.isEmpty() || price <= 0) {
            showAlert("Input Error", "Name cannot be empty and Price must be positive.");
            return;
        }

        // Find max ID and add 1 (for menu items, as 'id' in menu table is not IDENTITY in the SQL script)
        int nextId = 1;
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT NVL(MAX(id), 0) + 1 FROM menu")) { // Use NVL for empty table
            if (rs.next()) {
                nextId = rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Database Error", "Could not determine next ID for menu item: " + e.getMessage());
            return;
        }

        String sql = "INSERT INTO menu (id, name, price) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, nextId);
            ps.setString(2, name);
            ps.setDouble(3, price);
            int rowsAffected = ps.executeUpdate();
            if (rowsAffected > 0) {
                conn.commit();
                showAlert("Success", "Menu item added.");
                loadMenuItemsForAdmin(); // Refresh admin view
                loadMenuItems(); // Refresh order screen view
                clearAdminFields();
            } else {
                showAlert("Error", "Failed to add menu item. No rows affected.");
                conn.rollback();
            }
        } catch (SQLException e) {
            try { if (conn != null) conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            showAlert("Database Error", "Failed to add menu item: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void updateMenuItem() {
        if (conn == null) return;
        int id;
        double price;
        String name = adminItemNameField.getText();
        try {
            id = Integer.parseInt(adminItemIdField.getText());
            price = Double.parseDouble(adminItemPriceField.getText());
        } catch (NumberFormatException e) {
            showAlert("Input Error", "Please enter valid name and Price.");
            return;
        }
        if (name.isEmpty() || price <= 0) {
            showAlert("Input Error", "Name cannot be empty and Price must be positive.");
            return;
        }

        String sql = "UPDATE menu SET name = ?, price = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setDouble(2, price);
           // ps.setInt(3, id);
            int rowsAffected = ps.executeUpdate();
            if (rowsAffected > 0) {
                conn.commit();
                showAlert("Success", "Menu item updated.");
                loadMenuItemsForAdmin();
                loadMenuItems();
                clearAdminFields();
            } else {
                showAlert("Not Found", "No menu item found with ID: " + id);
                conn.rollback();
            }
        } catch (SQLException e) {
            try { if (conn != null) conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            showAlert("Database Error", "Failed to update menu item: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void deleteMenuItem() {
        if (conn == null) return;
        int id;
        try {
            id = Integer.parseInt(adminItemIdField.getText());
        } catch (NumberFormatException e) {
            showAlert("Input Error", "Please enter a valid ID to delete.");
            return;
        }

        // In a real app, consider foreign key constraints (e.g., if a menu item is part of a historical order)
        // For this simple app, we'll proceed with deletion.

        String sql = "DELETE FROM menu WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            int rowsAffected = ps.executeUpdate();
            if (rowsAffected > 0) {
                conn.commit();
                showAlert("Success", "Menu item deleted.");
                loadMenuItemsForAdmin();
                loadMenuItems();
                clearAdminFields();
            } else {
                showAlert("Not Found", "No menu item found with ID: " + id);
                conn.rollback();
            }
        } catch (SQLException e) {
            try { if (conn != null) conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            showAlert("Database Error", "Failed to delete menu item: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void clearAdminFields() {
        adminItemIdField.clear();
        adminItemNameField.clear();
        adminItemPriceField.clear();
    }

    private void loadOrderHistory() {
        orderHistoryData.clear();
        if (conn == null) {
            showAlert("Error", "Database connection is not established.");
            return;
        }
        // Join orders and bills to get the bill_time
        // If you are getting ORA-00942 here for 'orders' or 'bills', then those tables were not created successfully.
        // You MUST run the SQL script mentioned at the top of this file first.
        String sql = "SELECT o.order_id, o.bill_id, o.item_name, o.item_price, o.quantity, o.subtotal, b.bill_time " +
                     "FROM orders o JOIN bills b ON o.bill_id = b.bill_id ORDER BY b.bill_time DESC, o.order_id DESC";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                orderHistoryData.add(new OrderHistoryItem(
                    rs.getInt("order_id"),
                    rs.getInt("bill_id"),
                    rs.getString("item_name"),
                    rs.getDouble("item_price"),
                    rs.getInt("quantity"),
                    rs.getDouble("subtotal"),
                    rs.getTimestamp("bill_time")
                ));
            }
            System.out.println("Order history loaded: " + orderHistoryData.size());
        } catch (SQLException e) {
            showAlert("Load Orders Error", "Failed to load order history: " + e.getMessage() + "\n" +
                      "Ensure 'orders' and 'bills' tables exist and your database user has access.");
            e.printStackTrace();
        }
    }


    private void showAlert(String title, String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION); // Default to info
        if (title.contains("Error") || title.contains("Failed") || title.contains("Invalid") || title.contains("Not Found")) {
            alert.setAlertType(Alert.AlertType.ERROR);
        }
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }

    @Override
    public void stop() {
        if (conn != null) {
            try {
                conn.close();
                System.out.println("Oracle Database connection closed.");
            } catch (SQLException e) {
                System.err.println("Error closing Oracle database connection: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}