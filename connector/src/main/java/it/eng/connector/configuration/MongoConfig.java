package it.eng.connector.configuration;

import it.eng.catalog.model.Action;
import it.eng.catalog.model.LeftOperand;
import it.eng.catalog.model.Operator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

import java.util.Arrays;

@Configuration
@EnableMongoAuditing
@EnableMongoRepositories(basePackages = {"it.eng.tools.repository", "it.eng.tools.s3.repository",
        "it.eng.connector.repository",
        "it.eng.catalog.repository",
        "it.eng.negotiation.repository",
        "it.eng.datatransfer.repository"})

public class MongoConfig {
    @Bean
    public AuditorAware<String> auditorProvider() {
        return new AuditorAwareImpl();
    }

//    @Bean
//    public MongoCustomConversions mongoCustomConversions() {
//        return new MongoCustomConversions.MongoConverterBuilder()
//                .registerConverter(new StringToActionConverter())
//                .registerConverter(new ActionToStringConverter())
//                .registerConverter(new StringToLeftOperandConverter())
//                .registerConverter(new LeftOperandToStringConverter())
//                .registerConverter(new StringToOperatorConverter())
//                .registerConverter(new OperatorToStringConverter())
//                .build();
//    }

//    @Bean
//    public MongoCustomConversions mongoCustomConversions() {
//        return MongoCustomConversions.create(builder -> {
//            builder.configureConverters(adapter -> {
//                // Register converters with type hints to prevent unwanted conversions
//                adapter.registerConverter(new StringToActionConverter(),
//                        TypeDescriptor.valueOf(String.class),
//                        TypeDescriptor.valueOf(Action.class));
//                adapter.registerConverter(new ActionToStringConverter());
//
//                adapter.registerConverter(new StringToLeftOperandConverter(),
//                        TypeDescriptor.valueOf(String.class),
//                        TypeDescriptor.valueOf(LeftOperand.class));
//                adapter.registerConverter(new LeftOperandToStringConverter());
//
//                adapter.registerConverter(new StringToOperatorConverter(),
//                        TypeDescriptor.valueOf(String.class),
//                        TypeDescriptor.valueOf(Operator.class));
//                adapter.registerConverter(new OperatorToStringConverter());
//            });
//        });
//    }

    @Bean
    public MongoCustomConversions mongoCustomConversions() {
        return new MongoCustomConversions(Arrays.asList(
                new StringToActionConverter(),
                new ActionToStringConverter(),
                new StringToLeftOperandConverter(),
                new LeftOperandToStringConverter(),
                new StringToOperatorConverter(),
                new OperatorToStringConverter(),
                new StringToActionConverterCN(),
                new ActionToStringConverterCN(),
                new StringToLeftOperandConverterCN(),
                new LeftOperandToStringConverterCN(),
                new StringToOperatorConverterCN(),
                new OperatorToStringConverterCN()
        ));
    }

    @ReadingConverter
    public static class StringToActionConverter implements Converter<String, Action> {
        @Override
        public Action convert(String source) {
            return Action.fromString(source);
        }
    }

    @WritingConverter
    public static class ActionToStringConverter implements Converter<Action, String> {
        @Override
        public String convert(Action source) {
            return source.toString();
        }
    }

    @ReadingConverter
    public static class StringToActionConverterCN implements Converter<String, it.eng.negotiation.model.Action> {
        @Override
        public it.eng.negotiation.model.Action convert(String source) {
            return it.eng.negotiation.model.Action.fromString(source);
        }
    }

    @WritingConverter
    public static class ActionToStringConverterCN implements Converter<it.eng.negotiation.model.Action, String> {
        @Override
        public String convert(it.eng.negotiation.model.Action source) {
            return source.toString();
        }
    }

    @ReadingConverter
    public static class StringToLeftOperandConverter implements Converter<String, LeftOperand> {
        @Override
        public LeftOperand convert(String source) {
            return LeftOperand.fromString(source);
        }
    }

    @WritingConverter
    public static class LeftOperandToStringConverter implements Converter<LeftOperand, String> {
        @Override
        public String convert(LeftOperand source) {
            return source.toString();
        }
    }

    @ReadingConverter
    public static class StringToLeftOperandConverterCN implements Converter<String, it.eng.negotiation.model.LeftOperand> {
        @Override
        public it.eng.negotiation.model.LeftOperand convert(String source) {
            return it.eng.negotiation.model.LeftOperand.fromString(source);
        }
    }

    @WritingConverter
    public static class LeftOperandToStringConverterCN implements Converter<it.eng.negotiation.model.LeftOperand, String> {
        @Override
        public String convert(it.eng.negotiation.model.LeftOperand source) {
            return source.toString();
        }
    }

    @ReadingConverter
    public static class StringToOperatorConverter implements Converter<String, Operator> {
        @Override
        public Operator convert(String source) {
            return Operator.fromString(source);
        }
    }

    @WritingConverter
    public static class OperatorToStringConverter implements Converter<Operator, String> {
        @Override
        public String convert(Operator source) {
            return source.toString();
        }
    }

    @ReadingConverter
    public static class StringToOperatorConverterCN implements Converter<String, it.eng.negotiation.model.Operator> {
        @Override
        public it.eng.negotiation.model.Operator convert(String source) {
            return it.eng.negotiation.model.Operator.fromString(source);
        }
    }

    @WritingConverter
    public static class OperatorToStringConverterCN implements Converter<it.eng.negotiation.model.Operator, String> {
        @Override
        public String convert(it.eng.negotiation.model.Operator source) {
            return source.toString();
        }
    }
}


