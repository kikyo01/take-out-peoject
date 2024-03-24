package com.sky.service.impl;

import com.sky.constant.StatusConstant;
import com.sky.dto.GoodsSalesDTO;
import com.sky.entity.OrderDetail;
import com.sky.entity.Orders;
import com.sky.mapper.OrderDetailMapper;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.ReportService;
import com.sky.vo.OrderReportVO;
import com.sky.vo.SalesTop10ReportVO;
import com.sky.vo.TurnoverReportVO;
import com.sky.vo.UserReportVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ReportServiceImpl implements ReportService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private OrderDetailMapper orderDetailMapper;

    /**
     * 统计指定区间内营业额数据
     * @param begin
     * @param end
     * @return
     */
    public TurnoverReportVO getTurnoverStatistics(LocalDate begin, LocalDate end) {

        //当前集合用于存放begin到end范围内的每天的日期
        List<LocalDate> dateList = new ArrayList<>();
        dateList.add(begin);
        while(!begin.equals(end)){
            // 日期计算，指定日期的后一天对应日期
            begin = begin.plusDays(1);
            dateList.add(begin);
        }

        //存放每天的营业额
        List<Double> turnoverList = new ArrayList<>();

        for (LocalDate date : dateList) {
            //查询date这个日期对应的营业额数据，营业额是指：状态为：“已完成”的订单金额合计

            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);

            //select sum(amount) from orders where order_time > beginTime and order_time < endTime and status = 5
            //三个条件封装成map
            Map map = new HashMap();
            map.put("begin",beginTime);
            map.put("end",endTime);
            map.put("status", Orders.COMPLETED);
            Double turnover = orderMapper.sumByMap(map);
            turnover = turnover == null ? 0.0 : turnover;
            turnoverList.add(turnover);

        }

        return TurnoverReportVO
                .builder()
                .dateList(StringUtils.join(dateList,","))
                .turnoverList(StringUtils.join(turnoverList,","))
                .build();



    }

    /**
     * 统计指定时间内的用户数据
     * @param begin
     * @param end
     * @return
     */
    public UserReportVO getUserStatistics(LocalDate begin, LocalDate end) {
        //当前集合用于存放begin到end范围内的每天的日期
        List<LocalDate> dateList = new ArrayList<>();
        dateList.add(begin);
        while(!begin.equals(end)){
            // 日期计算，指定日期的后一天对应日期
            begin = begin.plusDays(1);
            dateList.add(begin);
        }

        //存放每天的用户数量
        List<Integer> newUserList = new ArrayList<>();
        //存放每天的新增用户
        List<Integer> totalUserList = new ArrayList<>();

        Integer S = 0;
        for (LocalDate date : dateList) {

            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);

            //总 select count(id) from user where create_time > ?
            //新增 select count(id) from user where create_time > ? and create_time < ?

            Map map = new HashMap();
            map.put("begin",beginTime);
            map.put("end",endTime);

            Integer count = userMapper.countByMap(map);
            newUserList.add(count);
            S += count;
            totalUserList.add(S);
        }

        return UserReportVO.builder()
                .dateList(StringUtils.join(dateList,","))
                .newUserList(StringUtils.join(newUserList,","))
                .totalUserList(StringUtils.join(totalUserList,","))
                .build();
    }

    /**
     * 统计指定时间内的订单数据
     * @param begin
     * @param end
     * @return
     */
    public OrderReportVO getOrderStatistics(LocalDate begin, LocalDate end) {
        //当前集合用于存放begin到end范围内的每天的日期
        List<LocalDate> dateList = new ArrayList<>();
        dateList.add(begin);
        while(!begin.equals(end)){
            // 日期计算，指定日期的后一天对应日期
            begin = begin.plusDays(1);
            dateList.add(begin);
        }

        //存放每天的订单数 select count(id) from orders where orderTime > ? and orderTime < ?
        List<Integer> orderCountList = new ArrayList<>();
        //存放每天的有效订单数 select count(id) from orders where orderTime > ? and orderTime < ? and status = 5
        List<Integer> validOrderCountList = new ArrayList<>();
        //订单总数
        Integer totalOrderCount = 0;
        //有效订单数
        Integer totalValidOrderCount = 0;
        //订单完成率
        Double orderCompletionRate = 0.0;

        for (LocalDate date : dateList) {

            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);

            //获取每天订单数
            Integer orderCount = getOrderCount(beginTime, endTime, null);
            Integer validOrderCount = getOrderCount(beginTime, endTime, Orders.COMPLETED);
            //加到两个列表
            orderCountList.add(orderCount);
            validOrderCountList.add(validOrderCount);
            //更新total (也可以用Stream流，优雅)
            totalOrderCount += orderCount;
            totalValidOrderCount += validOrderCount;
        }
        //计算订单完成率
        if(totalOrderCount != 0){
            orderCompletionRate = totalValidOrderCount.doubleValue() / totalOrderCount;
        }

        return OrderReportVO.builder()
                .dateList(StringUtils.join(dateList,","))
                .orderCountList(StringUtils.join(orderCountList,","))
                .validOrderCountList(StringUtils.join(validOrderCountList,","))
                .totalOrderCount(totalOrderCount)
                .validOrderCount(totalValidOrderCount)
                .orderCompletionRate(orderCompletionRate)
                .build();
    }


    /**
     * 根据条件统计订单数量
     * @param begin
     * @param end
     * @param status
     * @return
     */
    private Integer getOrderCount(LocalDateTime begin,LocalDateTime end,Integer status){
        Map map = new HashMap();
        map.put("begin",begin);
        map.put("end",end);
        map.put("status",status);
        return orderMapper.countByMap(map);
    }


    /**
     * 统计指定时间区间内销量前十
     * @param begin
     * @param end
     * @return
     */
    public SalesTop10ReportVO getSalesTop10(LocalDate begin, LocalDate end) {
        LocalDateTime beginTime = LocalDateTime.of(begin, LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.of(end, LocalTime.MAX);

        List<GoodsSalesDTO> salesTop10 = orderMapper.getSalesTop10(beginTime, endTime);

        List<String> names = salesTop10.stream().map(GoodsSalesDTO::getName).collect(Collectors.toList());
        String nameList = StringUtils.join(names, ",");

        List<Integer> numbers = salesTop10.stream().map(GoodsSalesDTO::getNumber).collect(Collectors.toList());
        String numberList = StringUtils.join(numbers, ",");

        //封装返回结果
        return SalesTop10ReportVO
                .builder()
                .nameList(nameList)
                .numberList(numberList)
                .build();
    }
}

