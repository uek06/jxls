package org.jxls.builder;

import static org.jxls.util.Util.getSheetsNameOfMultiSheetTemplate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

import org.jxls.area.Area;
import org.jxls.common.CellRef;
import org.jxls.common.Context;
import org.jxls.expression.ExpressionEvaluatorFactory;
import org.jxls.formula.FormulaProcessor;
import org.jxls.transform.JxlsTransformerFactory;
import org.jxls.transform.Transformer;
import org.jxls.util.CannotOpenWorkbookException;

public class JxlsTemplateFiller {
    protected final ExpressionEvaluatorFactory expressionEvaluatorFactory;
    protected final String expressionNotationBegin;
    protected final String expressionNotationEnd;
    protected final FormulaProcessor formulaProcessor;
    protected final boolean recalculateFormulasBeforeSaving;
    protected final boolean recalculateFormulasOnOpening;
    protected final boolean hideTemplateSheet;
    protected final boolean deleteTemplateSheet;
    protected final AreaBuilder areaBuilder;
    protected final boolean clearTemplateCells;
    protected final JxlsTransformerFactory transformerFactory;
    protected final JxlsStreaming streaming;
    protected final InputStream template;
    protected Transformer transformer;
    protected List<Area> areas;
    
    protected JxlsTemplateFiller(ExpressionEvaluatorFactory expressionEvaluatorFactory,
            String expressionNotationBegin, String expressionNotationEnd, //
            boolean recalculateFormulasBeforeSaving, boolean recalculateFormulasOnOpening, //
            FormulaProcessor formulaProcessor, boolean hideTemplateSheet, boolean deleteTemplateSheet, //
            AreaBuilder areaBuilder, boolean clearTemplateCells, JxlsTransformerFactory transformerFactory, JxlsStreaming streaming, //
            InputStream template) {
        this.expressionEvaluatorFactory = expressionEvaluatorFactory;
        this.expressionNotationBegin = expressionNotationBegin;
        this.expressionNotationEnd = expressionNotationEnd;
        this.recalculateFormulasBeforeSaving = recalculateFormulasBeforeSaving;
        this.recalculateFormulasOnOpening = recalculateFormulasOnOpening;
        this.formulaProcessor = formulaProcessor;
        this.hideTemplateSheet = hideTemplateSheet;
        this.deleteTemplateSheet = deleteTemplateSheet;
        this.areaBuilder = areaBuilder;
        this.clearTemplateCells = clearTemplateCells;
        this.transformerFactory = transformerFactory;
        this.streaming = streaming;
        this.template = template;
    }

    public void fill(Map<String, Object> data, JxlsOutput output) {
        try (OutputStream outputStream = output.getOutputStream()) {
            createTransformer(outputStream);
            configureTransformer();
            processTemplate(data);
            preWrite();
            write();
        } catch (CannotOpenWorkbookException up) {
        	throw up;
        } catch (IOException e) {
            throw new JxlsTemplateFillException(e);
        } finally {
            areas = null;
            transformer = null;
        }
    }

    protected ByteArrayOutputStream createOutputStream() {
        return new ByteArrayOutputStream();
    }

    protected void createTransformer(OutputStream outputStream) {
        transformer = transformerFactory.create(template, outputStream, streaming);
    }

    protected void configureTransformer() {
        transformer.getTransformationConfig().buildExpressionNotation(expressionNotationBegin, expressionNotationEnd);
        transformer.getTransformationConfig().setExpressionEvaluatorFactory(expressionEvaluatorFactory);
    }

    protected void processTemplate(Map<String, Object> data) {
        areas = areaBuilder.build(transformer, clearTemplateCells);
        Context context = new Context(data);
        for (Area area : areas) {
            area.applyAt(new CellRef(area.getStartCellRef().getCellName()), context);
        }
        if (formulaProcessor != null) {
            for (Area area : areas) {
                area.setFormulaProcessor(formulaProcessor);
                area.processFormulas();
            }
        }
    }

    protected void preWrite() {
        transformer.setEvaluateFormulas(recalculateFormulasBeforeSaving);
        transformer.setFullFormulaRecalculationOnOpening(recalculateFormulasOnOpening);
        if (hideTemplateSheet) {
            List<String> sheetNameTemplate = getSheetsNameOfMultiSheetTemplate(areas);
            for (String sheetName : sheetNameTemplate) {
                transformer.setHidden(sheetName, true);
            }
        }
        if (deleteTemplateSheet) {
            List<String> sheetNameTemplate = getSheetsNameOfMultiSheetTemplate(areas);
            for (String sheetName : sheetNameTemplate) {
                transformer.deleteSheet(sheetName);
            }
        }
    }

    protected void write() throws IOException {
        transformer.write();
    }
}
