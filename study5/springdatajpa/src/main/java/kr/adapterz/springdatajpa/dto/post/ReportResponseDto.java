package kr.adapterz.springdatajpa.dto.post;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ReportResponseDto {
    private String message = "report_success";
    private int report_count;
    public ReportResponseDto(int report_count){
        this.report_count=report_count;
    }
}
