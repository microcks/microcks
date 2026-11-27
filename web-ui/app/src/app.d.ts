declare global {
  namespace App {
    interface Locals {
      /** The Microcks access token of the signed-in user, set when authentication is enabled. */
      token?: string;
    }
  }
}

export {};
