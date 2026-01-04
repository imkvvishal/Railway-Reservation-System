import java.sql.*;
import java.util.Scanner;

public class railway1 {

    //  1. DATABASE CONFIGURATION - MUST BE UPDATED
    static final String JDBC_DRIVER = "com.mysql.cj.jdbc.Driver";
    // Change this if your port or host is different
    static final String DB_URL = "jdbc:mysql://localhost:3306/database_name";
    // *** REPLACE WITH YOUR ACTUAL MYSQL CREDENTIALS ***
    static final String USER = "MYSQL_username"; 
    static final String PASS = "MYSQL_password"; 

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        try {
            // Load the JDBC driver dynamically
            Class.forName(JDBC_DRIVER);
            
            // Menu Loop
            int choice = 0;
            do {
                System.out.println("\n\n===== Railway Reservation System =====");
                System.out.println("1. Display Available Trains");
                System.out.println("2. Book a Ticket");
                System.out.println("3. Exit");
                System.out.print("Enter your choice: ");
                
                if (scanner.hasNextInt()) {
                    choice = scanner.nextInt();
                    scanner.nextLine(); // Consume newline
                } else {
                    System.out.println("Invalid input. Please enter a number (1, 2, or 3).");
                    scanner.nextLine(); // Consume the invalid input
                    continue;
                }

                switch (choice) {
                    case 1:
                        displayTrains();
                        break;
                    case 2:
                        bookTicket(scanner);
                        break;
                    case 3:
                        System.out.println("Thank you for using the system. Goodbye!");
                        break;
                    default:
                        System.out.println("Invalid choice. Please try again.");
                }
            } while (choice != 3);

        } catch (ClassNotFoundException e) {
            System.err.println("JDBC Driver not found. Ensure 'mysql-connector-java.jar' is in your classpath.");
        } catch (Exception e) {
            System.err.println("An unexpected system error occurred: " + e.getMessage());
        } finally {
            if (scanner != null) {
                scanner.close();
            }
        }
    }
    
    // --------------------------------------------------------------------------------
    
    /**
     * Retrieves and displays all available trains and their seat counts from the database.
     */
    public static void displayTrains() {
        String sql = "SELECT train_number, train_name, source, destination, available_seats FROM trains";
        
        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            System.out.println("\n-----------------------------------------------------------");
            System.out.printf("| %-12s | %-15s | %-10s | %-12s | %-10s |\n", "Train No.", "Train Name", "Source", "Destination", "Seats");
            System.out.println("-----------------------------------------------------------");

            if (!rs.isBeforeFirst() ) {    
                System.out.println("| No trains currently available in the system.                |");
            }
            
            while (rs.next()) {
                int number = rs.getInt("train_number");
                String name = rs.getString("train_name");
                String source = rs.getString("source");
                String destination = rs.getString("destination");
                int seats = rs.getInt("available_seats");
                
                System.out.printf("| %-12d | %-15s | %-10s | %-12s | %-10d |\n", number, name, source, destination, seats);
            }
            System.out.println("-----------------------------------------------------------");

        } catch (SQLException se) {
            System.err.println("Error displaying trains: " + se.getMessage());
        }
    }
    
    // --------------------------------------------------------------------------------

    /**
     * Handles the ticket booking process using JDBC Transactions for atomicity.
     * This function checks availability, updates seats, and records the booking.
     */
    public static void bookTicket(Scanner scanner) {
        Connection conn = null;
        PreparedStatement checkStmt = null;
        PreparedStatement updateStmt = null;
        PreparedStatement insertStmt = null;
        ResultSet rs = null;

        System.out.print("Enter Train Number: ");
        int trainNumber = scanner.nextInt();
        scanner.nextLine(); 
        System.out.print("Enter Passenger Name: ");
        String passengerName = scanner.nextLine();
	System.out.println("Enter your age :");
	int age =scanner.nextInt();
	System.out.println("Enter gender :");
	String gender=scanner.next();
        System.out.println("Enter the date of journey (Year-Month-Date):");
        String dateOfJourney = scanner.next();
	 // Example date

        try {
            conn = DriverManager.getConnection(DB_URL, USER, PASS);
            // Disable auto-commit to start transaction
            conn.setAutoCommit(false); 

            // 1. Check Availability (and Lock the row using FOR UPDATE)
            String checkSql = "SELECT available_seats FROM trains WHERE train_number = ? FOR UPDATE";
            checkStmt = conn.prepareStatement(checkSql);
            checkStmt.setInt(1, trainNumber);
            rs = checkStmt.executeQuery();

            if (!rs.next()) {
                System.out.println("Booking Failed: Train " + trainNumber + " not found!");
                conn.rollback();
                return;
            }

            int currentSeats = rs.getInt("available_seats");

            if (currentSeats <= 0) {
                System.out.println("Booking Failed: No available seats on Train " + trainNumber);
                conn.rollback();
                return;
            }
            
            // 2. Update Seat Count (Decrease by 1)
            String updateSql = "UPDATE trains SET available_seats = available_seats - 1 WHERE train_number = ?";
            updateStmt = conn.prepareStatement(updateSql);
            updateStmt.setInt(1, trainNumber);
            updateStmt.executeUpdate();

            // 3. Insert Booking Record
            String insertSql = "INSERT INTO bookings (train_number, passenger_name, date_of_journey,age,gender) VALUES (?, ?, ?, ?, ?)";
            // Statement.RETURN_GENERATED_KEYS is needed to get the PNR_NUMBER
            insertStmt = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS);
            insertStmt.setInt(1, trainNumber);
            insertStmt.setString(2, passengerName);
            insertStmt.setString(3, dateOfJourney);
	    insertStmt.setInt(4,age);
	    insertStmt.setString(5,gender);
	   
            insertStmt.executeUpdate();

            // Retrieve the generated PNR number
            ResultSet pnrRs = insertStmt.getGeneratedKeys();
            int pnr = -1;
            if (pnrRs.next()) {
                pnr = pnrRs.getInt(1);
            }

            // Commit Transaction: Success
            conn.commit(); 
            System.out.println("\n Ticket Booked Successfully!");
            System.out.println("PNR Number: " + pnr);
            System.out.println("Train No: " + trainNumber + ", Passenger: " + passengerName);
            System.out.println("Remaining Seats: " + (currentSeats - 1));

        } catch (SQLException se) {
            // Rollback Transaction on error
            System.err.println("\n Booking Failed due to a database error. Reverting changes.");
            System.err.println("Error: " + se.getMessage());
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    System.err.println("Error rolling back transaction: " + ex.getMessage());
                }
            }
        } finally {
            // Ensure all JDBC resources are closed
            try { if (rs != null) rs.close(); } catch (SQLException e) { /* ignored */ }
            try { if (checkStmt != null) checkStmt.close(); } catch (SQLException e) { /* ignored */ }
            try { if (updateStmt != null) updateStmt.close(); } catch (SQLException e) { /* ignored */ }
            try { if (insertStmt != null) insertStmt.close(); } catch (SQLException e) { /* ignored */ }
            try { 
                if (conn != null) {
                    conn.setAutoCommit(true); // Reset auto-commit state
                    conn.close();
                }
            } catch (SQLException e) { /* ignored */ }
        }
    }
}


