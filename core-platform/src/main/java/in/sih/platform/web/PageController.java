package in.sih.platform.web;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {
    @GetMapping("/") public String landing() { return "forward:/pages/landing.html"; }
    @GetMapping("/login") public String login() { return "forward:/pages/login.html"; }
    @GetMapping("/explore") public String explore() { return "forward:/pages/explore.html"; }
    @GetMapping("/challenges/{id}") public String challenge() { return "forward:/pages/challenge.html"; }
    @GetMapping("/submit") @PreAuthorize("hasRole('CITIZEN')") public String submit() { return "forward:/pages/submit.html"; }
    @GetMapping("/my-challenges") @PreAuthorize("hasRole('CITIZEN')") public String mine() { return "forward:/pages/my-challenges.html"; }
    @GetMapping("/admin/validation") @PreAuthorize("hasRole('ADMIN')") public String validation() { return "forward:/pages/validation.html"; }
    @GetMapping("/admin/allocation") @PreAuthorize("hasRole('ADMIN')") public String allocation() { return "forward:/pages/allocation.html"; }
    @GetMapping("/universities/{id}/departments") public String departments() { return "forward:/pages/departments.html"; }
    @GetMapping("/university/inbox") @PreAuthorize("hasRole('UNIVERSITY')") public String inbox() { return "forward:/pages/inbox.html"; }
    @GetMapping("/projects/{id}") @PreAuthorize("hasAnyRole('UNIVERSITY','ADMIN','GOVERNMENT','INDUSTRY')") public String project() { return "forward:/pages/project.html"; }
    @GetMapping("/industry") @PreAuthorize("hasRole('INDUSTRY')") public String industry() { return "forward:/pages/industry.html"; }
    @GetMapping("/government") @PreAuthorize("hasRole('GOVERNMENT')") public String government() { return "forward:/pages/government.html"; }
}
