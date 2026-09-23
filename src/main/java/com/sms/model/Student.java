package com.sms.model;

/**
 * Represents a single student - one row of the {@code students} table.
 *
 * <p>This is a plain data holder: it knows nothing about SQL and nothing about
 * the console. Keeping it ignorant of both is what lets the DAO, the service and
 * the UI all share the same object.
 */
public class Student {

    private Long id;
    private String name;
    private String email;
    private String phone;
    private int age;
    private String course;

    /**
     * No-argument constructor.
     *
     * <p>Needed when a student is rebuilt field by field, e.g. from a ResultSet
     * row, and by libraries that create objects through reflection.
     */
    public Student() {
    }

    /**
     * Constructor for a student that does not exist in the database yet.
     *
     * <p>There is deliberately no {@code id} parameter: the database assigns the
     * id, so an unsaved student cannot have one.
     */
    public Student(String name, String email, String phone, int age, String course) {
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.age = age;
        this.course = course;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public String getCourse() {
        return course;
    }

    public void setCourse(String course) {
        this.course = course;
    }

    @Override
    public String toString() {
        return String.format(
                "Student{id=%s, name='%s', email='%s', phone='%s', age=%d, course='%s'}",
                id, name, email, phone, age, course);
    }
}
