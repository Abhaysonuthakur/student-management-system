package com.sms;

import com.sms.dao.StudentDAO;
import com.sms.dao.StudentDAOImpl;
import com.sms.exception.DataAccessException;
import com.sms.exception.DuplicateEmailException;
import com.sms.exception.StudentNotFoundException;
import com.sms.exception.ValidationException;
import com.sms.model.Student;
import com.sms.service.StudentService;

import java.util.List;
import java.util.Scanner;

/**
 * Console front end for the Student Management System.
 *
 * <p>This class reads input, calls the service, and prints the result. It holds
 * no business rules and no SQL - which is why the same rules will still work
 * when a REST controller replaces this menu.
 *
 * <p>It is also the <strong>composition root</strong>: the single place where
 * the application decides which {@link StudentDAO} implementation to use.
 */
public class Main {

    private static final Scanner SCANNER = new Scanner(System.in);

    private static StudentService service;

    public static void main(String[] args) {
        // The composition root: the only line in the whole application that
        // mentions a concrete storage implementation.
        service = new StudentService(new StudentDAOImpl());

        System.out.println("=== Student Management System ===");

        boolean running = true;
        while (running) {
            printMenu();
            running = handleMenuChoice(readInt("Choose an option: "));
        }

        System.out.println("Goodbye.");
        // System.in is deliberately left open: the JVM owns it.
    }

    private static void printMenu() {
        System.out.println();
        System.out.println("  1. Add student");
        System.out.println("  2. View all students");
        System.out.println("  3. Find student by id");
        System.out.println("  4. Search students");
        System.out.println("  5. Update student");
        System.out.println("  6. Delete student");
        System.out.println("  0. Exit");
    }

    /**
     * Runs one menu option and turns every failure into a friendly message.
     *
     * <p>This is the application's single exception boundary. The two catch
     * blocks separate the two kinds of failure that matter: something the user
     * can fix, and something they cannot.
     *
     * @return {@code false} when the user asked to exit
     */
    private static boolean handleMenuChoice(int choice) {
        try {
            switch (choice) {
                case 1 -> addStudent();
                case 2 -> viewAllStudents();
                case 3 -> findStudent();
                case 4 -> searchStudents();
                case 5 -> updateStudent();
                case 6 -> deleteStudent();
                case 0 -> {
                    return false;
                }
                default -> System.out.println("  Unknown option: " + choice);
            }
        } catch (ValidationException | DuplicateEmailException | StudentNotFoundException e) {
            // The user's problem, and the message already explains it.
            System.out.println("  ! " + e.getMessage());
        } catch (DataAccessException e) {
            // Our problem. Say something useful and keep the detail for the log.
            System.out.println("  ! Database problem, please try again.");
            System.out.println("    (technical detail: " + e.getCause().getMessage() + ")");
        }
        return true;
    }

    private static void addStudent() {
        Student student = readStudentDetails();
        long id = service.addStudent(student);
        System.out.println("  Saved. The database assigned id " + id);
    }

    private static void viewAllStudents() {
        List<Student> students = service.findAllStudents();

        if (students.isEmpty()) {
            System.out.println("  No students yet.");
            return;
        }

        System.out.println("  " + students.size() + " student(s):");
        students.forEach(s -> System.out.println("    " + s));
    }

    private static void findStudent() {
        long id = readInt("  Student id: ");
        System.out.println("  " + service.findStudent(id));
    }

    private static void searchStudents() {
        String keyword = readLine("  Search name or course: ");
        List<Student> results = service.searchStudents(keyword);

        if (results.isEmpty()) {
            System.out.println("  No matches for \"" + keyword.strip() + "\".");
            return;
        }

        System.out.println("  " + results.size() + " match(es):");
        results.forEach(s -> System.out.println("    " + s));
    }

    private static void updateStudent() {
        long id = readInt("  Student id to update: ");

        // Fails fast with StudentNotFoundException if the id is unknown, so the
        // user is not asked to retype five fields for a student who isn't there.
        Student current = service.findStudent(id);
        System.out.println("  Current: " + current);
        System.out.println("  Enter the new details:");

        Student updated = readStudentDetails();
        updated.setId(id);

        boolean changed = service.updateStudent(updated);
        System.out.println(changed ? "  Updated." : "  Nothing was updated.");
    }

    private static void deleteStudent() {
        long id = readInt("  Student id to delete: ");
        Student current = service.findStudent(id);

        System.out.println("  About to delete: " + current);
        String answer = readLine("  Type yes to confirm: ");

        if (!"yes".equalsIgnoreCase(answer.strip())) {
            System.out.println("  Cancelled.");
            return;
        }

        boolean deleted = service.deleteStudent(id);
        System.out.println(deleted ? "  Deleted." : "  Nothing was deleted.");
    }

    private static Student readStudentDetails() {
        String name = readLine("  Name  : ");
        String email = readLine("  Email : ");
        String phone = readLine("  Phone : ");
        int age = readInt("  Age   : ");
        String course = readLine("  Course: ");
        return new Student(name, email, phone, age, course);
    }

    /**
     * Reads one line of text.
     *
     * <p>If the input stream ends (a piped script, or Ctrl+Z on Windows) we exit
     * instead of looping forever on a stream that will never produce more input.
     */
    private static String readLine(String prompt) {
        System.out.print(prompt);

        if (!SCANNER.hasNextLine()) {
            System.out.println();
            System.out.println("Input ended. Goodbye.");
            System.exit(0);
        }
        return SCANNER.nextLine();
    }

    /**
     * Reads a whole number, re-prompting until the user types one.
     *
     * <p>Note this reads a <em>line</em> and parses it, rather than calling
     * {@code Scanner.nextInt()}. That matters: {@code nextInt()} leaves the
     * newline in the buffer, so the next {@code nextLine()} returns an empty
     * string and your program appears to skip a question. Reading lines and
     * parsing them yourself avoids the whole problem.
     */
    private static int readInt(String prompt) {
        while (true) {
            String line = readLine(prompt).strip();
            try {
                return Integer.parseInt(line);
            } catch (NumberFormatException e) {
                System.out.println("  \"" + line + "\" is not a whole number.");
            }
        }
    }
}
