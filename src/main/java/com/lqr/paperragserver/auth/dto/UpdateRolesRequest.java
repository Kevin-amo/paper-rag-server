package com.lqr.paperragserver.auth.dto;

import java.util.List;

public record UpdateRolesRequest(List<String> roles) {
}