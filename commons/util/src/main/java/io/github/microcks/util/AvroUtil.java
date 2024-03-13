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

import org.apache.avro.AvroTypeException;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.Encoder;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.io.JsonDecoder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Helper class using utility methods for converting Avro format from and to JSON.
 * @author laurent
 */
public class AvroUtil {

   /**
    * Convert a JSON string into an Avro binary representation using specified schema.
    * @param json       A JSON string to convert to Avro
    * @param avroSchema String representation of an Avro Schema to use for conversion
    * @return The Avro binary representation of JSON
    * @throws AvroTypeException if there's a mismatch between JSON string and Avro Schema
    * @throws IOException       if something goes wrong during conversion
    */
   public static byte[] jsonToAvro(String json, String avroSchema) throws AvroTypeException, IOException {
      return jsonToAvro(json, new Schema.Parser().parse(avroSchema));
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
      // Prepare reader an input stream from Json string.
      GenericDatumReader<Object> reader = new GenericDatumReader<>(avroSchema);
      InputStream input = new ByteArrayInputStream(json.getBytes("UTF-8"));
      JsonDecoder jsonDecoder = DecoderFactory.get().jsonDecoder(avroSchema, input);

      // Prepare write and output stream to produce binary encoding.
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      GenericDatumWriter<Object> writer = new GenericDatumWriter<>(avroSchema);
      Encoder e = EncoderFactory.get().binaryEncoder(baos, null);

      // Fill a datum object from jsonDecoder.
      Object datum = null;
      try {
         while (true) {
            datum = reader.read(datum, jsonDecoder);
            writer.write(datum, e);
            e.flush();
         }
      } catch (EOFException eofException) {
         // Nothing to do here, we just exited the loop.
      }
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
      return jsonToAvroRecord(json, new Schema.Parser().parse(avroSchema));
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
      // Prepare reader an input stream from Json string.
      GenericDatumReader<GenericRecord> reader = new GenericDatumReader<>(avroSchema);
      InputStream input = new ByteArrayInputStream(json.getBytes("UTF-8"));
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
      return avroToJson(avroBinary, new Schema.Parser().parse(avroSchema));
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
      DatumReader<GenericRecord> datumReader = new GenericDatumReader<>(avroSchema);
      Decoder decoder = DecoderFactory.get().binaryDecoder(avroBinary, null);

      GenericRecord record = datumReader.read(null, decoder);
      return record.toString();
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
      return avroToAvroRecord(avroBinary, new Schema.Parser().parse(avroSchema));
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
      DatumReader<GenericRecord> datumReader = new GenericDatumReader<>(avroSchema);
      Decoder decoder = DecoderFactory.get().binaryDecoder(avroBinary, null);

      return datumReader.read(null, decoder);
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
            if (datum instanceof GenericRecord) {
               GenericRecord record = (GenericRecord) datum;
               for (Schema.Field f : schema.getFields()) {
                  if (!record.hasField(f.name()))
                     return false;
                  if (!validate(f.schema(), record.get(f.pos())))
                     return false;
               }
               return true;
            }
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

      switch (schema.getType()) {
         case RECORD:
            if (datum instanceof GenericRecord) {
               GenericRecord record = (GenericRecord) datum;
               for (Schema.Field f : schema.getFields()) {
                  // Check for defined and required field.
                  if (!record.hasField(f.name()) && !f.hasDefaultValue()) {
                     errors.add("Required field " + f.name() + " cannot be found in record");
                  } else if (record.hasField(f.name())) {
                     // Now add errors for each field if defined at the record level.
                     errors.addAll(getValidationErrors(f.schema(), record.get(f.pos()), f.name()));
                  }
               }
            }
            break;
         case ENUM:
            if (!schema.hasEnumSymbol(datum.toString()))
               errors.add(datum.toString() + " enum value is not defined in schema");
            break;
         case ARRAY:
            if (!(datum instanceof Collection)) {
               errors.add(fieldName + " is not a valid array");
            } else {
               // Now add errors for each element.
               for (Object element : (Collection) datum) {
                  errors.addAll(getValidationErrors(schema.getElementType(), element));
               }
            }
            break;
         case STRING:
            if (!(datum instanceof CharSequence))
               errors.add(fieldName + " is not a string");
            break;
         case BYTES:
            if (!(datum instanceof ByteBuffer))
               errors.add(fieldName + " is not bytes");
            break;
         case INT:
            if (!(datum instanceof Integer))
               errors.add(fieldName + " is not an integer");
            break;
         case LONG:
            if (!(datum instanceof Long))
               errors.add(fieldName + " is not a long");
            break;
         case FLOAT:
            if (!(datum instanceof Float))
               errors.add(fieldName + " is not a float");
            break;
         case DOUBLE:
            if (!(datum instanceof Double))
               errors.add(fieldName + " is not a double");
            break;
         case BOOLEAN:
            if (!(datum instanceof Boolean))
               errors.add(fieldName + " is not a boolean");
            break;
      }
      return errors;
   }
}
