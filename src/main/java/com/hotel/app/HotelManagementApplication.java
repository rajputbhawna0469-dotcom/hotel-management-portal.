package com.hotel.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.ui.Model;
import jakarta.persistence.*;
import jakarta.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@SpringBootApplication
@Controller
@CrossOrigin(origins = "*")
public class HotelManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(HotelManagementApplication.class, args);
    }

    private final EmployeeRepository employeeRepo;
    private final AttendanceRepository attendanceRepo;

    public HotelManagementApplication(EmployeeRepository employeeRepo, AttendanceRepository attendanceRepo) {
        this.employeeRepo = employeeRepo;
        this.attendanceRepo = attendanceRepo;
    }

    // --- HOME & LOGIN VIEWS ---
    @GetMapping("/")
    public String indexPage() {
        return "index"; 
    }

    @GetMapping("/login")
    public String showLoginPage() {
        return "login"; 
    }

    // Smart Login Logic: Detects Admin vs Employee via Email & Password
    @PostMapping("/login")
    public String processLogin(@RequestParam("email") String email, @RequestParam("password") String password, HttpSession session, Model model) {
        
        // 1. Admin Login Check
        if ("admin@hotel.com".equalsIgnoreCase(email)) {
            if ("admin123".equals(password)) {
                session.setAttribute("loggedInUser", "admin");
                return "redirect:/admin-dashboard";
            } else {
                model.addAttribute("error", "Invalid Admin Password!");
                return "login";
            }
        }

        // 2. Employee Login Check (Using their unique password stored in database)
        Employee emp = employeeRepo.findByEmail(email);
        if (emp != null) {
            if (emp.getPassword() != null && emp.getPassword().equals(password)) {
                session.setAttribute("loggedInUser", emp.getEmail());
                return "redirect:/employee-dashboard?email=" + email;
            } else {
                model.addAttribute("error", "Incorrect Password for this Employee account!");
                return "login";
            }
        }

        model.addAttribute("error", "Email ID not registered in the system!");
        return "login";
    }

    // Admin Dashboard Route (Full Access: Add employees, view all data)
    @GetMapping("/admin-dashboard")
    public String showAdminDashboard(HttpSession session, Model model) {
        try {
            model.addAttribute("employees", employeeRepo.findAll());
            model.addAttribute("attendances", attendanceRepo.findAll());
        } catch (Exception e) {
            model.addAttribute("employees", new ArrayList<>());
            model.addAttribute("attendances", new ArrayList<>());
        }
        return "admin-dashboard"; 
    }

    // Employee Personal Dashboard Route (Read-Only view for specific employee)
    @GetMapping("/employee-dashboard")
    public String showEmployeeDashboard(@RequestParam("email") String email, Model model) {
        Employee emp = employeeRepo.findByEmail(email);
        if (emp == null) {
            return "redirect:/login";
        }
        List<Attendance> attendances = attendanceRepo.findByEmpId(emp.getId());
        
        model.addAttribute("employee", emp);
        model.addAttribute("attendances", attendances);
        return "employee-dashboard";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }

    // --- EMPLOYEE JSON APIs ---
    @GetMapping("/api/employees")
    @ResponseBody
    public List<Employee> getAllEmployees() {
        return employeeRepo.findAll();
    }

    @PostMapping("/api/employees")
    @ResponseBody
    public Employee saveOrUpdateEmployee(@RequestBody Employee employee) {
        if (employee.getId() != null) {
            Optional<Employee> existing = employeeRepo.findById(employee.getId());
            if (existing.isPresent()) {
                Employee old = existing.get();
                if (employee.getPassword() == null || employee.getPassword().isEmpty()) {
                    employee.setPassword(old.getPassword());
                }
                if (employee.getPhoto() == null || employee.getPhoto().isEmpty()) employee.setPhoto(old.getPhoto());
                if (employee.getContractDoc() == null || employee.getContractDoc().isEmpty()) employee.setContractDoc(old.getContractDoc());
                if (employee.getTaxDoc() == null || employee.getTaxDoc().isEmpty()) employee.setTaxDoc(old.getTaxDoc());
                if (employee.getNdaDoc() == null || employee.getNdaDoc().isEmpty()) employee.setNdaDoc(old.getNdaDoc());
            }
        }
        return employeeRepo.save(employee);
    }

    @DeleteMapping("/api/employees/{id}")
    @ResponseBody
    public void deleteEmployee(@PathVariable Long id) {
        employeeRepo.deleteById(id);
    }

    // --- ATTENDANCE JSON APIs ---
    @PostMapping("/api/attendance")
    @ResponseBody
    public Attendance markAttendance(@RequestBody Attendance attendance) {
        Optional<Attendance> existingLog = attendanceRepo.findByEmpIdAndDate(attendance.getEmpId(), attendance.getDate());
        if (existingLog.isPresent()) {
            Attendance log = existingLog.get();
            log.setStatus(attendance.getStatus());
            return attendanceRepo.save(log);
        }
        return attendanceRepo.save(attendance);
    }

    @GetMapping("/api/attendance")
    @ResponseBody
    public List<Attendance> getAllAttendance() {
        return attendanceRepo.findAll();
    }

    @GetMapping("/api/attendance/employee/{empId}")
    @ResponseBody
    public List<Attendance> getAttendanceByEmployee(@PathVariable Long empId) {
        return attendanceRepo.findByEmpId(empId);
    }

    // --- PAYROLL API ---
    @GetMapping("/api/payroll")
    @ResponseBody
    public List<PayrollSummary> getPayroll() {
        List<Employee> employees = employeeRepo.findAll();
        List<Attendance> attendances = attendanceRepo.findAll();
        List<PayrollSummary> summary = new ArrayList<>();

        for (Employee emp : employees) {
            double fullDays = attendances.stream()
                    .filter(a -> a.getEmpId().equals(emp.getId()) && "Present".equalsIgnoreCase(a.getStatus()))
                    .count();

            double halfDays = attendances.stream()
                    .filter(a -> a.getEmpId().equals(emp.getId()) && "Half Day".equalsIgnoreCase(a.getStatus()))
                    .count();

            double totalDaysWorked = fullDays + (halfDays * 0.5);
            double dailyRate = emp.getBaseSalary() / 30.0;
            double netSalary = Math.round(dailyRate * totalDaysWorked * 100.0) / 100.0;

            summary.add(new PayrollSummary(emp.getId(), emp.getName(), emp.getRole(), emp.getBaseSalary(), totalDaysWorked, netSalary));
        }
        return summary;
    }
}

// --- EMPLOYEE ENTITY ---
@Entity
class Employee {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String dept;
    private String role;
    private double baseSalary;
    private String phone;

    private String email;
    private String password; // Unique password for employee login
    
    private String emergencyContact;
    private String reportsTo;
    private String workLocation;
    private String status;
    private String hireDate;
    private String tenure;
    private String keycardId;

    private String bankName;
    private String accountHolder;
    private String accountNumber;
    private String ifscCode;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String photo;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String contractDoc;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String taxDoc;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String ndaDoc;

    public Employee() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDept() { return dept; }
    public void setDept(String dept) { this.dept = dept; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public double getBaseSalary() { return baseSalary; }
    public void setBaseSalary(double baseSalary) { this.baseSalary = baseSalary; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getEmergencyContact() { return emergencyContact; }
    public void setEmergencyContact(String emergencyContact) { this.emergencyContact = emergencyContact; }
    public String getReportsTo() { return reportsTo; }
    public void setReportsTo(String reportsTo) { this.reportsTo = reportsTo; }
    public String getWorkLocation() { return workLocation; }
    public void setWorkLocation(String workLocation) { this.workLocation = workLocation; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getHireDate() { return hireDate; }
    public void setHireDate(String hireDate) { this.hireDate = hireDate; }
    public String getTenure() { return tenure; }
    public void setTenure(String tenure) { this.tenure = tenure; }
    public String getKeycardId() { return keycardId; }
    public void setKeycardId(String keycardId) { this.keycardId = keycardId; }

    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }
    public String getAccountHolder() { return accountHolder; }
    public void setAccountHolder(String accountHolder) { this.accountHolder = accountHolder; }
    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
    public String getIfscCode() { return ifscCode; }
    public void setIfscCode(String ifscCode) { this.ifscCode = ifscCode; }

    public String getPhoto() { return photo; }
    public void setPhoto(String photo) { this.photo = photo; }

    public String getContractDoc() { return contractDoc; }
    public void setContractDoc(String contractDoc) { this.contractDoc = contractDoc; }

    public String getTaxDoc() { return taxDoc; }
    public void setTaxDoc(String taxDoc) { this.taxDoc = taxDoc; }

    public String getNdaDoc() { return ndaDoc; }
    public void setNdaDoc(String ndaDoc) { this.ndaDoc = ndaDoc; }
}

// --- ATTENDANCE ENTITY ---
@Entity
class Attendance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long empId;
    private String date;
    private String status;

    public Attendance() {}

    public Long getId() { return id; }
    public Long getEmpId() { return empId; }
    public void setEmpId(Long empId) { this.empId = empId; }
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}

// --- REPOSITORIES ---
@Repository
interface EmployeeRepository extends JpaRepository<Employee, Long> {
    Employee findByEmail(String email);
}

@Repository
interface AttendanceRepository extends JpaRepository<Attendance, Long> {
    List<Attendance> findByEmpId(Long empId);
    Optional<Attendance> findByEmpIdAndDate(Long empId, String date);
}

// --- PAYROLL DTO ---
class PayrollSummary {
    private Long empId;
    private String name;
    private String role;
    private double baseSalary;
    private double daysWorked;
    private double netSalary;

    public PayrollSummary(Long empId, String name, String role, double baseSalary, double daysWorked, double netSalary) {
        this.empId = empId;
        this.name = name;
        this.role = role;
        this.baseSalary = baseSalary;
        this.daysWorked = daysWorked;
        this.netSalary = netSalary;
    }

    public Long getEmpId() { return empId; }
    public String getName() { return name; }
    public String getRole() { return role; }
    public double getBaseSalary() { return baseSalary; }
    public double getDaysWorked() { return daysWorked; }
    public double getNetSalary() { return netSalary; }
}