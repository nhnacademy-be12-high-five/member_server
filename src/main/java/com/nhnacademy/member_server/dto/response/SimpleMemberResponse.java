package com.nhnacademy.member_server.dto.response;  
  
import lombok.AllArgsConstructor;  
import lombok.Builder;  
import lombok.Getter;  
import lombok.NoArgsConstructor;  
  
@Getter  
@Builder  
@NoArgsConstructor  
@AllArgsConstructor  
public class SimpleMemberResponse {  
    private Long memberId;  
    private String loginId;  
}