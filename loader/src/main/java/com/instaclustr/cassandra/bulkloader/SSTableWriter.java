package com.instaclustr.cassandra.bulkloader;

import static java.lang.String.format;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;

import com.instaclustr.cassandra.bulkloader.specs.BulkLoaderSpec;
import org.apache.cassandra.io.sstable.CQLSSTableWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SSTableWriter {

    private static final Logger logger = LoggerFactory.getLogger(SSTableWriter.class);

    private final BulkLoaderSpec spec;
    private final Path tableDir;
    private final String insertStatement;

    public SSTableWriter(final BulkLoaderSpec spec, final String insertStatement) throws SSTableGeneratorException {
        this.spec = spec;
        this.insertStatement = insertStatement;

        tableDir = Paths.get(spec.outputDir, spec.keyspace, spec.table);

        if (!Files.exists(tableDir)) {
            try {
                Files.createDirectories(tableDir);
            } catch (IOException ex) {
                throw new SSTableGeneratorException(format("Unable to create directory %s", tableDir), ex.getCause());
            }
        }
    }

    private CQLSSTableWriter getWriter() {

        final CQLSSTableWriter.Builder builder = CQLSSTableWriter.builder();

        String createSchemaStatement;

        try {
            if (!Files.exists(Paths.get(spec.schema))) {
                throw new IllegalStateException(format("Schema file %s does not exist!", spec.schema));
            }

            createSchemaStatement = new String(Files.readAllBytes(Paths.get(spec.schema)));
        } catch (Exception ex) {
            throw new SSTableGeneratorException(String.format("Unable to read schema at %s", spec.schema), ex);
        }

        builder.inDirectory(tableDir.toFile())
               .forTable(createSchemaStatement)
               .using(insertStatement)
               .withBufferSizeInMB(spec.bufferSize);

        if (spec.sorted) {
            builder.sorted();
        }

        builder.withPartitioner(spec.partitioner);

        return builder.build();
    }

    public void generate(final Iterator<MappedRow> rowsIterator) {

        MappedRow actualRow = null;

        try (final CQLSSTableWriter writer = getWriter()) {
            while (rowsIterator.hasNext()) {

                actualRow = rowsIterator.next();

                if (actualRow.values != null && !actualRow.values.isEmpty()) {
                    writer.addRow(actualRow.values);
                }
            }
        } catch (IOException ex) {
            if (actualRow != null) {
                logger.error(format("Unable to write row using values %s with types %s by insert statement %s",
                                    actualRow.values, actualRow.types, insertStatement),
                             ex);
            } else {
                logger.error("Unable to write", ex);
            }
        }
    }
}
