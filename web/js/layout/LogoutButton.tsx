// Copyright © 2015-2019 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import React from "react";
import {logout} from "../api";

const LogoutButton = () => <button type="button" className="pure-button" onClick={handleClick}>Logout</button>;

async function handleClick() {
  await logout();
  window.location.href = '/';
}

export default LogoutButton;
