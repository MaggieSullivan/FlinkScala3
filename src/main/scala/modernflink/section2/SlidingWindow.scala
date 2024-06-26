package modernflink.section2

import modernflink.model.BankingEventGenerator
import modernflink.model.BankingEventGenerator.{Deposit, DepositEventGenerator}
import modernflink.section2.Given.given
import org.apache.flink.api.common.eventtime.{SerializableTimestampAssigner, WatermarkStrategy}
import org.apache.flink.streaming.api.windowing.assigners.{
  GlobalWindows,
  SlidingEventTimeWindows,
  TumblingProcessingTimeWindows
}
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.api.windowing.triggers.{CountTrigger, PurgingTrigger}
import org.apache.flink.streaming.api.windowing.windows.{GlobalWindow, TimeWindow, Window}
import org.apache.flink.util.Collector
import org.apache.flinkx.api.serializers.*
import org.apache.flinkx.api.StreamExecutionEnvironment
import org.apache.flinkx.api.function.AllWindowFunction

import java.time.{Duration, Instant}

class DepositBySlidingWindow extends AllWindowFunction[Deposit, String, TimeWindow]:
  override def apply(
      window: TimeWindow,
      input: Iterable[Deposit],
      out: Collector[String]
  ): Unit =
    out.collect(s"${window.getStart} to ${window.getEnd}: $input")
object SlidingWindow:

  val env = StreamExecutionEnvironment.getExecutionEnvironment

  val depositData = env
    .addSource(
      new DepositEventGenerator(
        sleepSeconds = 1,
        startTime = Instant.parse("2023-08-13T00:00:00.00Z")
      )
    )
    .assignTimestampsAndWatermarks(
      WatermarkStrategy
        .forBoundedOutOfOrderness(java.time.Duration.ofMillis(0))
        .withTimestampAssigner(new SerializableTimestampAssigner[Deposit] {
          override def extractTimestamp(
              element: Deposit,
              recordTimestamp: Long
          ) =
            element.time.toEpochMilli
        })
    )

  def main(args: Array[String]): Unit =
    createSlidingWindowStream()

  def createSlidingWindowStream(): Unit =
    val depositByWindowStream = depositData
      .windowAll(
        SlidingEventTimeWindows.of(
          Time.milliseconds(200),
          Time.milliseconds(100)
        )
      )

    val slidingWindowStream =
      depositByWindowStream.apply(new DepositBySlidingWindow)

    slidingWindowStream.print()
    env.execute()
