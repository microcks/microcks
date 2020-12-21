/*
 * Licensed to Laurent Broudoux (the "Author") under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Author licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.github.microcks.minion.async.format;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.Encoder;
import org.apache.avro.io.EncoderFactory;

import org.junit.jupiter.api.Test;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test case method for AvroUtil class.
 * @author laurent
 */
public class AvroUtilTest {

   @Test
   public void testAvroBasics() {
      Schema schema = null;

      try {
         // Load schema from file.
         schema = new Schema.Parser().parse(new File("target/test-classes/io/github/microcks/minion/async/format/user.avsc"));

         GenericRecord user1 = new GenericData.Record(schema);
         user1.put("name", "Laurent");
         user1.put("email", "laurent@microcks.io");
         user1.put("age", 41);

         GenericRecord user2 = new GenericData.Record(schema);
         user2.put("name", "John");
         user2.put("email", "john@microcks.io");
         user2.put("age", 23);

         // Serialize using Json encoding.
         ByteArrayOutputStream baos = new ByteArrayOutputStream();
         PrintStream out = new PrintStream(new BufferedOutputStream(baos));

         DatumWriter<GenericRecord> datumWriter = new GenericDatumWriter<GenericRecord>(schema);
         Encoder encoder = EncoderFactory.get().jsonEncoder(schema, out, false);
         datumWriter.write(user1, encoder);
         datumWriter.write(user2, encoder);
         encoder.flush();
         String jsonEncoding = baos.toString("UTF-8");
         System.err.println("jsonEncoding: \n" + jsonEncoding);

         // Serialize using binary encoding.
         baos = new ByteArrayOutputStream();
         out = new PrintStream(new BufferedOutputStream(baos));
         encoder = EncoderFactory.get().binaryEncoder(out, null);
         datumWriter.write(user1, encoder);
         datumWriter.write(user2, encoder);
         encoder.flush();
         byte[] binaryRepresentation = baos.toByteArray();
         String binaryEncoding = new String(binaryRepresentation, "UTF-8");
         System.err.println("\nbinaryEncoding: \n" + binaryEncoding);

         // Deserialize from binary encoding representation.
         DatumReader<GenericRecord> datumReader = new GenericDatumReader<GenericRecord>(schema);
         GenericRecord user = null;
         Decoder decoder = DecoderFactory.get().binaryDecoder(binaryRepresentation, null);

         try {
            while (true) {
               user = datumReader.read(user, decoder);
               System.err.println("User from binary representation: " + user);
            }
         } catch (EOFException eofException) {
            // Nothing to do here, just exit the while loop.
         }
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }
   }

   @Test
   public void testJsonToAvro() {
      String jsonText = "{\"name\":\"Laurent Broudoux\", \"email\":\"laurent@microcks.io\", \"age\":41}";

      try {
         // Load schema from file.
         Schema schema = new Schema.Parser().parse(new File("target/test-classes/io/github/microcks/minion/async/format/user.avsc"));

         // Convert back and forth to and from JSON.
         byte[] avroBinary = AvroUtil.jsonToAvro(jsonText, schema);
         System.err.println("binaryEncoding: \n" + new String(avroBinary, "UTF-8"));
         String jsonRepresentation = AvroUtil.avroToJson(avroBinary, schema);
         System.err.println("\njsonRepresentation: \n" + jsonRepresentation);

         assertTrue(jsonRepresentation.contains("\"Laurent Broudoux\""));
         assertTrue(jsonRepresentation.contains("\"laurent@microcks.io\""));
         assertTrue(jsonRepresentation.contains("41"));

         // Deserialize from binary encoding representation.
         DatumReader<GenericRecord> datumReader = new GenericDatumReader<GenericRecord>(schema);
         GenericRecord user = null;
         Decoder decoder = DecoderFactory.get().binaryDecoder(avroBinary, null);

         try {
            while (true) {
               user = datumReader.read(user, decoder);
               System.err.println("\nUser from binary representation: \n" + user.toString());
            }
         } catch (EOFException eofException) {
            // Nothing to do here, just exit the while loop.
         }
         assertEquals("Laurent Broudoux", user.get("name").toString());
         assertEquals("laurent@microcks.io", user.get("email").toString());
         assertEquals(41, (Integer) user.get("age"));
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }
   }

   @Test
   public void testJsonToAvroRecord() {
      String jsonText = "{\"name\":\"Laurent Broudoux\", \"email\":\"laurent@microcks.io\", \"age\":42}";

      try {
         // Load schema from file.
         Schema schema = new Schema.Parser().parse(new File("target/test-classes/io/github/microcks/minion/async/format/user.avsc"));

         GenericRecord record = AvroUtil.jsonToAvroRecord(jsonText, schema);
         assertEquals("Laurent Broudoux", record.get("name").toString());
         assertEquals("laurent@microcks.io", record.get("email").toString());
         assertEquals(42, Integer.valueOf(record.get("age").toString()));
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }
   }
}
