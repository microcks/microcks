export class User {
  login: string;
  id: number;
  name: string;
  email: string;
  avatar: string;

  constructor() {
    this.login = "";
    this.name = "";
    this.email = "";
  }
}