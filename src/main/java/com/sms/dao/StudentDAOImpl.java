package com.sms.dao;

import com.sms.model.Student;
import com.sms.util.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * MySQL implementation of {@link StudentDAO}.
 *
 * <p>This is the only class in the application that knows SQL exists. If the
 * storage ever changes, this class is replaced and nothing else moves.
 */
public class StudentDAOImpl implements StudentDAO {

    private static final String INSERT_SQL =
            "INSERT INTO students (name, email, phone, age, course) VALUES (?, ?, ?, ?, ?)";

    private static final String SELECT_BY_ID_SQL =
            "SELECT id, name, email, phone, age, course FROM students WHERE id = ?";

    private static final String SELECT_BY_EMAIL_SQL =
            "SELECT id, name, email, phone, age, course FROM students WHERE email = ?";

    private static final String SELECT_ALL_SQL =
            "SELECT id, name, email, phone, age, course FROM students ORDER BY id";

    private static final String SEARCH_SQL =
            "SELECT id, name, email, phone, age, course FROM students "
                    + "WHERE name LIKE ? OR course LIKE ? ORDER BY name";

    private static final String UPDATE_SQL =
            "UPDATE students SET name = ?, email = ?, phone = ?, age = ?, course = ? "
                    + "WHERE id = ?";

    private static final String DELETE_SQL =
            "DELETE FROM students WHERE id = ?";

    @Override
    public long insert(Student student) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, student.getName());
            ps.setString(2, student.getEmail());
            ps.setString(3, student.getPhone());
            ps.setInt(4, student.getAge());
            ps.setString(5, student.getCourse());

            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
                throw new SQLException(
                        "INSERT succeeded but MySQL returned no generated id");
            }
        }
    }

    @Override
    public Optional<Student> findById(long id) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_BY_ID_SQL)) {

            ps.setLong(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        }
    }

    @Override
    public Optional<Student> findByEmail(String email) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_BY_EMAIL_SQL)) {

            ps.setString(1, email);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        }
    }

    @Override
    public List<Student> findAll() throws SQLException {
        List<Student> students = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_ALL_SQL);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                students.add(mapRow(rs));
            }
        }
        return students;
    }

    @Override
    public List<Student> search(String keyword) throws SQLException {
        String pattern = "%" + keyword + "%";
        List<Student> students = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SEARCH_SQL)) {

            ps.setString(1, pattern);
            ps.setString(2, pattern);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    students.add(mapRow(rs));
                }
            }
        }
        return students;
    }

    @Override
    public boolean update(Student student) throws SQLException {
        Long id = student.getId();
        if (id == null) {
            throw new IllegalArgumentException(
                    "Cannot update a student that has no id: " + student);
        }

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPDATE_SQL)) {

            ps.setString(1, student.getName());
            ps.setString(2, student.getEmail());
            ps.setString(3, student.getPhone());
            ps.setInt(4, student.getAge());
            ps.setString(5, student.getCourse());
            ps.setLong(6, id);

            return ps.executeUpdate() == 1;
        }
    }

    @Override
    public boolean deleteById(long id) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(DELETE_SQL)) {

            ps.setLong(1, id);

            return ps.executeUpdate() == 1;
        }
    }

    /**
     * Converts the current row of a {@link ResultSet} into a {@link Student}.
     *
     * <p>Private on purpose: it is an implementation detail. Callers receive
     * {@link Student} objects and never see a {@code ResultSet}.
     */
    private Student mapRow(ResultSet rs) throws SQLException {
        Student student = new Student();
        student.setId(rs.getLong("id"));
        student.setName(rs.getString("name"));
        student.setEmail(rs.getString("email"));
        student.setPhone(rs.getString("phone"));
        student.setAge(rs.getInt("age"));
        student.setCourse(rs.getString("course"));
        return student;
    }
}
