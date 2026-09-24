/*
 * Copyright The Microcks Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.microcks.util;

import org.apache.avro.AvroRuntimeException;
import org.apache.avro.AvroTypeException;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.Encoder;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.io.JsonDecoder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Helper class using utility methods for converting Avro format from and to JSON.
 * @author laurent
 */
public class AvroUtil {

   private AvroUtil() {
      // Private constructor to hide implicit public one.
   }

   /**
    * Convert a Avro schema string into a Schema object.
    * @param avroSchema String representation of an Avro Schema to use for conversion
    * @return The Avro Schema to use
    * @throws org.apache.avro.SchemaParseException if the schema is not valid
    */
   public static Schema getSchema(String avroSchema) {
      return new Schema.Parser().parse(avroSchema);
   }

   /**
    * Convert a JSON string into an Avro binary representation using specified schema.
    * @param json       A JSON string to convert to Avro
    * @param avroSchema String representation of an Avro Schema to use for conversion
    * @return The Avro binary representation of JSON
    * @throws AvroTypeException if there's a mismatch between JSON string and Avro Schema
    * @throws IOException       if something goes wrong during conversion
    */
   public static byte[] jsonToAvro(String json, String avroSchema) throws AvroTypeException, IOException {
      return jsonToAvro(json, getSchema(avroSchema));
   }

   /**
    * Convert a JSON string into an Avro binary representation using specified schema.
    * @param json       A JSON string to convert to Avro
    * @param avroSchema The Avro Schema to use for conversion
    * @return The Avro binary representation of JSON
    * @throws AvroTypeException if there's a mismatch between JSON string and Avro Schema
    * @throws IOException       if something goes wrong during conversion
    */
   public static byte[] jsonToAvro(String json, Schema avroSchema) throws AvroTypeException, IOException {
      if (avroSchema.isUnion()) {
         // If the schema is a union, we need to find the right schema to use.
         for (Schema schema : avroSchema.getTypes()) {
            try {
               return jsonToAvro(json, schema);
            } catch (AvroRuntimeException | IOException e) {
               // Ignore and try next schema.
            }
         }
         throw new AvroTypeException("No schema in union matches JSON data");
      }
      // Prepare reader an input stream from Json string.
      GenericDatumReader<GenericRecord> reader = new GenericDatumReader<>(avroSchema);
      InputStream input = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
      JsonDecoder jsonDecoder = DecoderFactory.get().jsonDecoder(avroSchema, input);

      // Prepare write and output stream to produce binary encoding.
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      GenericDatumWriter<Object> writer = new GenericDatumWriter<>(avroSchema);
      Encoder e = EncoderFactory.get().binaryEncoder(baos, null);

      // Read the data into a GenericRecord.
      GenericRecord datum = reader.read(null, jsonDecoder);

      // Write the GenericRecord to the Avro binary.
      writer.write(datum, e);
      e.flush();

      return baos.toByteArray();
   }

   /**
    * Convert a JSON string into an Avro GenericRecord object using specified schema.
    * @param json       A JSON string to convert to Avro
    * @param avroSchema String representation of an Avro Schema to use for conversion
    * @return The GenericRecord representation of JSON
    * @throws AvroTypeException if there's a mismatch between JSON string and Avro Schema
    * @throws IOException       if something goes wrong during conversion
    */
   public static GenericRecord jsonToAvroRecord(String json, String avroSchema) throws AvroTypeException, IOException {
      return jsonToAvroRecord(json, getSchema(avroSchema));
   }

   /**
    * Convert a JSON string into an Avro GenericRecord object using specified schema.
    * @param json       A JSON string to convert to Avro
    * @param avroSchema The Avro Schema to use for conversion
    * @return The GenericRecord representation of JSON
    * @throws AvroTypeException if there's a mismatch between JSON string and Avro Schema
    * @throws IOException       if something goes wrong during conversion
    */
   public static GenericRecord jsonToAvroRecord(String json, Schema avroSchema) throws AvroTypeException, IOException {
      if (avroSchema.isUnion()) {
         // If the schema is a union, we need to find the right schema to use.
         for (Schema schema : avroSchema.getTypes()) {
            try {
               return jsonToAvroRecord(json, schema);
            } catch (AvroRuntimeException | IOException e) {
               // Ignore and try next schema.
            }
         }
         throw new AvroTypeException("No schema in union matches JSON data");
      }

      // Prepare reader an input stream from Json string.
      GenericDatumReader<GenericRecord> reader = new GenericDatumReader<>(avroSchema);
      InputStream input = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
      JsonDecoder jsonDecoder = DecoderFactory.get().jsonDecoder(avroSchema, input);

      return reader.read(null, jsonDecoder);
   }

   /**
    * Convert an Avro binary representation into a JSON string using specified schema.
    * @param avroBinary An Avro binary representation to convert in JSON string
    * @param avroSchema The Avro Schema to use for conversion
    * @return The JSON string representing Avro binary
    * @throws AvroTypeException if there's a mismatch between Avro binary and Schema
    * @throws IOException       if something goes wrong during conversion
    */
   public static String avroToJson(byte[] avroBinary, String avroSchema) throws AvroTypeException, IOException {
      return avroToJson(avroBinary, getSchema(avroSchema));
   }

   /**
    * Convert an Avro binary representation into a JSON string using specified schema.
    * @param avroBinary An Avro binary representation to convert in JSON string
    * @param avroSchema The Avro Schema to use for conversion
    * @return The JSON string representing Avro binary
    * @throws AvroTypeException if there's a mismatch between Avro binary and Schema
    * @throws IOException       if something goes wrong during conversion
    */
   public static String avroToJson(byte[] avroBinary, Schema avroSchema) throws AvroTypeException, IOException {
      if (avroSchema.isUnion()) {
         return unionBinaryToAvroRecord(avroBinary, avroSchema).toString();
      }
      DatumReader<GenericRecord> datumReader = new GenericDatumReader<>(avroSchema);
      Decoder decoder = DecoderFactory.get().binaryDecoder(avroBinary, null);

      GenericRecord genRecord = datumReader.read(null, decoder);
      return genRecord.toString();
   }

   /**
    * Convert an Avro binary representation into an Avro GenericRecord object using specified schema.
    * @param avroBinary An Avro binary representation to convert in record
    * @param avroSchema The Avro Schema to use for conversion
    * @return The JSON string representing Avro binary
    * @throws AvroTypeException if there's a mismatch between Avro binary and Schema
    * @throws IOException       if something goes wrong during conversion
    */
   public static GenericRecord avroToAvroRecord(byte[] avroBinary, String avroSchema)
         throws AvroTypeException, IOException {
      return avroToAvroRecord(avroBinary, getSchema(avroSchema));
   }

   /**
    * Convert an Avro binary representation into an Avro GenericRecord object using specified schema.
    * @param avroBinary An Avro binary representation to convert in record
    * @param avroSchema The Avro Schema to use for conversion
    * @return The JSON string representing Avro binary
    * @throws AvroTypeException if there's a mismatch between Avro binary and Schema
    * @throws IOException       if something goes wrong during conversion
    */
   public static GenericRecord avroToAvroRecord(byte[] avroBinary, Schema avroSchema)
         throws AvroTypeException, IOException {
      if (avroSchema.isUnion()) {
         return unionBinaryToAvroRecord(avroBinary, avroSchema);
      }
      DatumReader<GenericRecord> datumReader = new GenericDatumReader<>(avroSchema);
      Decoder decoder = DecoderFactory.get().binaryDecoder(avroBinary, null);

      return datumReader.read(null, decoder);
   }

   /**
    * Read an Avro binary whose root schema is a union, supporting both the standard union encoding (with a leading
    * zig-zag branch index) and the "bare-branch" encoding produced by Microcks itself (see {@link #jsonToAvro}), which
    * omits that index. Each candidate decoding is only accepted when it consumes the whole binary, so a bare payload is
    * not silently misread as an indexed one (and vice versa).
    * @param avroBinary  The Avro binary to read
    * @param unionSchema The union Schema to read the binary against
    * @return The decoded GenericRecord
    * @throws AvroTypeException if no union branch and no encoding can fully read the binary
    */
   private static GenericRecord unionBinaryToAvroRecord(byte[] avroBinary, Schema unionSchema)
         throws AvroTypeException {
      // First, try the bare-branch encoding: each branch is read as a standalone schema, with no leading index.
      // This is what the Microcks producer emits and must keep working.
      for (Schema schema : unionSchema.getTypes()) {
         GenericRecord record = readFully(avroBinary, schema);
         if (record != null) {
            return record;
         }
      }
      // Then, try the standard union encoding, letting the reader consume the leading branch index.
      GenericRecord record = readFully(avroBinary, unionSchema);
      if (record != null) {
         return record;
      }
      throw new AvroTypeException("No schema in union matches Avro binary data");
   }

   /**
    * Try to fully read an Avro binary against a given schema. Returns the decoded record only if reading succeeds and
    * consumes the entire binary; returns {@code null} otherwise so the caller can try another schema or encoding.
    */
   private static GenericRecord readFully(byte[] avroBinary, Schema schema) {
      try {
         BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(avroBinary, null);
         GenericDatumReader<GenericRecord> datumReader = new GenericDatumReader<>(schema);
         GenericRecord record = datumReader.read(null, decoder);
         // Only accept the decoding if the whole binary has been consumed. This is what distinguishes a genuine
         // match from an accidental partial read (e.g. an index byte mistaken for a field length).
         if (decoder.isEnd()) {
            return record;
         }
      } catch (AvroRuntimeException | IOException | ArrayIndexOutOfBoundsException | NegativeArraySizeException
            | ClassCastException e) {
         // Not a match for this schema/encoding, let the caller try the next one.
      }
      return null;
   }

   /**
    * Validate that a datum object (typically a GenericRecord read somewhere but the method signature is loosely coupled
    * to make it recursive friendly) is compliant with an Avro schema.
    * @param schema The Avro Schema to validate datum against
    * @param datum  The Object datum to validate
    * @return True if the object is compliant with supplied schema, false otherwise.
    */
   public static boolean validate(Schema schema, Object datum) {
      switch (schema.getType()) {
         case RECORD:
            if (datum instanceof GenericRecord genericRecord) {
               for (Schema.Field f : schema.getFields()) {
                  if (!genericRecord.hasField(f.name()))
                     return false;
                  if (!validate(f.schema(), genericRecord.get(f.pos())))
                     return false;
               }
               return true;
            }
            return GenericData.get().validate(schema, datum);
         default:
            return GenericData.get().validate(schema, datum);
      }
   }

   /**
    * Get validation errors of a datum object regarding Avro schema.
    * @param schema    The Schema to check datum object against
    * @param datum     The datum object to validate
    * @param fieldName The name of the field we're currently validating
    * @return A list of String representing validation errors. List may be empty if no error found.
    */
   public static List<String> getValidationErrors(Schema schema, Object datum, String... fieldName) {
      List<String> errors = new ArrayList<>();

      // fieldName is optional (top-level, ARRAY and UNION recursions may not provide one), so fall back to a
      // meaningful label to avoid an ArrayIndexOutOfBoundsException when reporting a type mismatch.
      String name = (fieldName != null && fieldName.length > 0) ? fieldName[0] : schema.getFullName();

      switch (schema.getType()) {
         case RECORD:
            if (datum instanceof GenericRecord genericRecord) {
               for (Schema.Field f : schema.getFields()) {
                  // Check for defined and required field.
                  if (!genericRecord.hasField(f.name()) && !f.hasDefaultValue()) {
                     errors.add("Required field " + f.name() + " cannot be found in record");
                  } else if (genericRecord.hasField(f.name())) {
                     // Now add errors for each field if defined at the record level.
                     errors.addAll(getValidationErrors(f.schema(), genericRecord.get(f.pos()), f.name()));
                  }
               }
            }
            break;
         case ENUM:
            if (!schema.hasEnumSymbol(datum.toString()))
               errors.add(datum + " enum value is not defined in schema");
            break;
         case ARRAY:
            if (!(datum instanceof Collection<?> collection)) {
               errors.add(name + " is not a valid array");
            } else {
               // Now add errors for each element.
               for (Object element : collection) {
                  errors.addAll(getValidationErrors(schema.getElementType(), element, name));
               }
            }
            break;
         case STRING:
            if (!(datum instanceof CharSequence))
               errors.add(name + " is not a string");
            break;
         case BYTES:
            if (!(datum instanceof ByteBuffer))
               errors.add(name + " is not bytes");
            break;
         case INT:
            if (!(datum instanceof Integer))
               errors.add(name + " is not an integer");
            break;
         case LONG:
            if (!(datum instanceof Long))
               errors.add(name + " is not a long");
            break;
         case FLOAT:
            if (!(datum instanceof Float))
               errors.add(name + " is not a float");
            break;
         case DOUBLE:
            if (!(datum instanceof Double))
               errors.add(name + " is not a double");
            break;
         case BOOLEAN:
            if (!(datum instanceof Boolean))
               errors.add(name + " is not a boolean");
            break;
         case UNION:
            // Get validation errors for each type in union.
            for (Schema unionSchema : schema.getTypes()) {
               errors.addAll(getValidationErrors(unionSchema, datum, name));
            }
            break;
      }
      return errors;
   }
}
