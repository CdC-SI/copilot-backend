package zas.admin.zec.backend.users;

import java.util.List;

public record User(String username, String password, List<Role> roles) {
}
